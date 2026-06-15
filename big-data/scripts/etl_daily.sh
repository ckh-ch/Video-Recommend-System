#!/bin/bash
# =============================================
# etl_daily.sh - 每日增量备份脚本
# crontab: 0 3 * * * /opt/scripts/etl_daily.sh
# 说明: MySQL 增量 → HDFS → ODS 分区 → DWD 分区
# =============================================

set -e
YESTERDAY=$(date -d yesterday +%Y-%m-%d)
MYSQL_JDBC="jdbc:mysql://192.168.126.130:3306/video_recommend"
MYSQL_USER="root"
MYSQL_PWD="123456"
HDFS_INC="/user/video-recommend/raw/increment/$YESTERDAY"
LOG="/var/log/etl_${YESTERDAY}.log"

log() { echo "[$(date '+%H:%M:%S')] $1" | tee -a $LOG; }

log "=== 开始增量备份: $YESTERDAY ==="

# ===== Step 1: 03:00 MySQL 增量导出 =====
log "Step 1: Sqoop 导出 MySQL 增量数据到 HDFS"
sqoop import \
  --connect $MYSQL_JDBC \
  --username $MYSQL_USER --password $MYSQL_PWD \
  --table user_behavior \
  --where "behavior_time >= '$YESTERDAY 00:00:00' AND behavior_time < '$YESTERDAY 23:59:59'" \
  --target-dir $HDFS_INC \
  --fields-terminated-by ',' \
  --as-textfile \
  --num-mappers 1 2>&1 | tee -a $LOG

# ===== Step 2: 加载 ODS 分区 + ODS → DWD 清洗 =====
log "Step 2: ODS 分区加载 + DWD 清洗"
hive -e "
USE video_recommend;
ALTER TABLE ods_user_behavior ADD IF NOT EXISTS PARTITION (dt='$YESTERDAY')
LOCATION '$HDFS_INC';

INSERT OVERWRITE TABLE dwd_user_behavior_clean PARTITION (dt='$YESTERDAY')
SELECT DISTINCT
    user_id, video_id,
    NVL(video_category, 'unknown'),
    NVL(like_type, 0),
    NVL(relay_type, 0),
    viewing_time,
    FROM_UNIXTIME(UNIX_TIMESTAMP(behavior_time, 'yyyy-MM-dd HH:mm:ss'))
FROM ods_user_behavior
WHERE dt = '$YESTERDAY'
  AND viewing_time IS NOT NULL
  AND viewing_time > 0;
" 2>&1 | tee -a $LOG

log "=== 增量备份完成: $YESTERDAY ==="
log "后续步骤: 在本地执行 ColdStartApp 更新推荐"
