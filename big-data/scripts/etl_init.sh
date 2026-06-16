#!/bin/bash
# =============================================
# etl_init.sh - 首次全量初始化脚本
# 执行: bash etl_init.sh
# 说明: HDFS CSV → ODS → DWD → MySQL user_behavior
# =============================================

export JAVA_HOME=/usr/local/jdk1.8.0_112
export HADOOP_HOME=/usr/local/hadoop-3.3.6
export HIVE_HOME=/usr/local/hive-3.1.3
export SQOOP_HOME=/usr/local/sqoop-1.4.7
export PATH=$JAVA_HOME/bin:$HADOOP_HOME/bin:$HIVE_HOME/bin:$SQOOP_HOME/bin:$PATH

set -e
BASE_DATE="2026-06-08"
HDFS_RAW="/user/video-recommend/raw"
HDFS_CSV="/user/video-recommend/raw/dy_action_view.csv"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MYSQL_HOST="192.168.126.130"
MYSQL_USER="root"
MYSQL_PWD="123456"

log() { echo "[$(date '+%H:%M:%S')] $1"; }

# ===== Step 1: 确认基准数据存在 =====
log "Step 1: 确认 HDFS 基准数据"
if hdfs dfs -test -e $HDFS_CSV; then
    log "  基准数据已存在: $HDFS_CSV"
else
    log "  上传基准数据到 HDFS..."
    hdfs dfs -mkdir -p $HDFS_RAW
    hdfs dfs -put /data/dy_action_view.csv $HDFS_CSV
fi

# ===== Step 2: 执行 Hive DDL =====
log "Step 2: 创建 Hive 分层表（ODS + DWD）"
hive -f "$SCRIPT_DIR/hive_ddl.sql"
log "  表创建完成"

# ===== Step 3: 加载 ODS 基准分区 dt=$BASE_DATE =====
log "Step 3: 加载 ODS 基准分区 dt=$BASE_DATE"
hdfs dfs -mkdir -p /user/video-recommend/ods/user_behavior/dt=$BASE_DATE
hdfs dfs -cp -f $HDFS_CSV /user/video-recommend/ods/user_behavior/dt=$BASE_DATE/
hive -e "
  USE video_recommend;
  ALTER TABLE ods_user_behavior ADD IF NOT EXISTS PARTITION (dt='$BASE_DATE');
"
log "  ODS 分区加载完成"

# ===== Step 4: ODS → DWD 清洗 =====
log "Step 4: ODS → DWD 清洗（约 3-5 分钟）"
hive -e "
  USE video_recommend;
  INSERT OVERWRITE TABLE dwd_user_behavior_clean PARTITION (dt='$BASE_DATE')
  SELECT DISTINCT
      user_id,
      video_id,
      NVL(video_category, 'unknown'),
      NVL(like_type, 0),
      NVL(relay_type, 0),
      viewing_time,
      FROM_UNIXTIME(UNIX_TIMESTAMP(behavior_time, 'yyyy-MM-dd HH:mm:ss'))
  FROM ods_user_behavior
  WHERE dt = '$BASE_DATE'
    AND viewing_time IS NOT NULL
    AND viewing_time > 0;
"
log "  DWD 清洗完成"

# ===== Step 5: DWD → MySQL user_behavior（灾难恢复） =====
log "Step 5: DWD → MySQL user_behavior（使用 Sqoop 导出）"
log "  清空 MySQL user_behavior 表..."
sqoop eval \
  --connect "jdbc:mysql://192.168.126.130:3306/video_recommend?useSSL=false" \
  --username root --password 123456 \
  --query "TRUNCATE user_behavior" 2>&1 | grep -v -E "SLF4J|which: no hbase|Warning:"
hdfs dfs -rm -r /tmp/dwd_csv_export 2>/dev/null || true
hive -e "
  USE video_recommend;
  INSERT OVERWRITE DIRECTORY '/tmp/dwd_csv_export'
  ROW FORMAT DELIMITED FIELDS TERMINATED BY ','
  SELECT user_id, video_id, video_category,
         CAST(like_type AS STRING), CAST(relay_type AS STRING),
         CAST(viewing_time AS STRING),
         FROM_UNIXTIME(UNIX_TIMESTAMP(behavior_time)) as behavior_time
  FROM dwd_user_behavior_clean
  WHERE dt = '${BASE_DATE}';
"
sqoop export \
  --connect "jdbc:mysql://192.168.126.130:3306/video_recommend?useSSL=false" \
  --username root --password 123456 \
  --table user_behavior \
  --columns "user_id,video_id,video_category,like_type,relay_type,viewing_time,behavior_time" \
  --export-dir /tmp/dwd_csv_export \
  --input-fields-terminated-by ',' \
  --input-null-string '\\N' \
  --input-null-non-string '\\N' \
  --num-mappers 1 \
  --batch 2>&1 | grep -v -E "SLF4J|which: no hbase|Warning:"
log "  MySQL user_behavior 已恢复（${BASE_DATE} 数据）"

log "=== 初始化完成! ==="
log "已导入: $BASE_DATE 的 ODS 和 DWD 数据"
log "已恢复: MySQL user_behavior 表"
log "下一步: 在本地执行 ColdStartApp --overwrite-behavior 生成推荐"
