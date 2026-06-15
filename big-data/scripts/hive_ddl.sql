-- =============================================
-- Hive 备份表 DDL - 视频推荐系统
-- 作用：ODS 原始备份 + DWD 清洗备份
-- 执行: hive -f hive_ddl.sql
-- =============================================

CREATE DATABASE IF NOT EXISTS video_recommend;
USE video_recommend;

-- =============================================
-- ODS 层：原始数据层（外部表，映射 HDFS CSV）
-- =============================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods_user_behavior (
    user_id       BIGINT,
    video_id      BIGINT,
    video_category STRING,
    like_type     INT,
    relay_type    INT,
    behavior_time STRING,
    viewing_time  DOUBLE
)
COMMENT 'ODS: 原始用户行为数据'
PARTITIONED BY (dt STRING)
ROW FORMAT DELIMITED FIELDS TERMINATED BY ','
LOCATION '/user/video-recommend/ods/user_behavior';

-- =============================================
-- DWD 层：明细清洗层（Parquet 列存）
-- =============================================
CREATE TABLE IF NOT EXISTS dwd_user_behavior_clean (
    user_id       BIGINT,
    video_id      BIGINT,
    video_category STRING,
    like_type     INT,
    relay_type    INT,
    viewing_time  DOUBLE,
    behavior_time TIMESTAMP
)
COMMENT 'DWD: 清洗后的用户行为明细'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;
