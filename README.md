# 短视频推荐系统 · 数据大屏

基于用户行为数据的短视频推荐系统，提供个性化推荐、用户画像分析及可视化数据大屏。

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术栈](#2-技术栈)
3. [项目结构](#3-项目结构)
4. [数据流设计](#4-数据流设计)
5. [API 接口文档](#5-api-接口文档)
6. [数据库设计](#6-数据库设计)
7. [前端大屏](#7-前端大屏)
8. [大数据模块](#8-大数据模块)
9. [启动方式](#9-启动方式)
10. [VM 运维管理](#10-vm-运维管理)

---

## 1. 项目概述

| 项目 | 说明 |
|------|------|
| 功能 | 短视频推荐 + 用户画像 + 可视化数据大屏 |
| 数据规模 | 约 198 万条用户行为数据，1000 用户，124873 视频 |
| 算法 | ALS 协同过滤 + 用户画像统计 |
| 大屏模式 | 全局数据大屏 / 用户画像大屏 双模式切换 |
| 缓存层 | Redis（5 分钟 TTL 自动缓存 + 24h 推荐/画像预写入） |
| 备份层 | Hive ODS → DWD 分层备份（VM crontab 每日 03:00） |

---

## 2. 技术栈

| 模块 | 技术 | 版本 |
|------|------|------|
| **前端** | Vue 3 + Vite + ECharts | 3.5.x / 8.x |
| **后端 API** | Spring Boot + MyBatis | 4.0.6 |
| **大数据** | Spark MLlib (local 模式) | 3.5.4 |
| **数据库** | MySQL | 8.x (VM 192.168.126.130:3306) |
| **缓存** | Redis | 7.x (VM 192.168.126.130:6379) |
| **消息队列** | Kafka | 3.x (VM 192.168.126.130:9092) |
| **数仓备份** | Hive (ODS + DWD) | 3.1.3 (VM 192.168.126.133) |

---

## 3. 项目结构

```
video-recommend-system/
├── video-front/                    # Vue 3 前端
│   └── src/
│       ├── views/
│       │   └── DashboardPage.vue   # 大屏主页面（全局/用户双模式）
│       ├── components/dashboard/   # 大屏图表组件（12个）
│       ├── api/index.js            # Axios API 封装
│       ├── router/index.js         # 路由（仅 /dashboard）
│       ├── constants.js            # 共享常量
│       └── assets/main.css         # 全局样式
│
├── video-recommend/                # Spring Boot 后端
│   └── src/main/java/org/example/videorecommend/
│       ├── controller/
│       │   ├── DashboardController.java    # 大屏统计接口（12个，全部带缓存）
│       │   ├── RecommendController.java    # 推荐接口（3个）
│       │   └── UserBehaviorController.java # 行为记录接口（2个）
│       ├── mapper/                         # MyBatis Mapper
│       ├── service/                        # 推荐服务
│       ├── entity/                         # 实体类（8个）
│       ├── config/
│       │   ├── CorsConfig.java             # CORS 跨域配置
│       │   ├── RedisCacheUtil.java         # 通用 Redis 缓存工具类
│       │   └── CachePreWarm.java           # 启动预热（自动缓存慢查询）
│       └── resources/
│           ├── application.properties
│           ├── schema.sql
│           └── mapper/UserBehaviorMapper.xml
│
├── big-data/                       # Spark 大数据处理
│   ├── src/main/scala/com/video/
│   │   ├── ColdStartApp.scala       # 冷启动：读 MySQL → ALS → 写 MySQL + Redis
│   │   └── streaming/StreamingApp.scala  # 实时流：消费 Kafka → 更新 Redis
│   └── scripts/
│       ├── hive_ddl.sql             # Hive 备份表 DDL（ODS + DWD）
│       ├── etl_init.sh              # 首次全量初始化（HDFS → ODS → DWD → MySQL）
│       └── etl_daily.sh             # 每日增量备份（MySQL → ODS → DWD）
│
└── docs/
    ├── etl/etl-design.md
    └── plans/upgrade-plan.md
```

---

## 4. 数据流设计

### 4.1 冷启动流程

```
MySQL user_behavior 表（行为数据）
    ↓
ColdStartApp.scala（本地运行）
    ├── 第一次运行（--overwrite-behavior）:
    │    ├── 从 MySQL 读取 → 写入 MySQL users/videos/user_behavior
    │    └── ALS 训练 → 写 MySQL + Redis
    └── 增量运行:
         ├── 从 MySQL 读取全量数据
         └── ALS 训练 → 写 MySQL + Redis（刷新推荐）
```

### 4.2 实时数据流

```
前端点赞/观看 → POST /api/behavior
    ├── → MySQL user_behavior 表（持久化）
    └── → Kafka topic "user_behavior"
                ↓
          StreamingApp.scala（每10秒消费）
                ↓
          Redis 实时更新:
          ├── profile:{uid}:cats（分类计数+1）
          ├── profile:{uid}:stats（totalWatch+1, totalLike+1）
          ├── dashboard:total_behaviors（全局计数+1）
          ├── dashboard:category_counts（分类热度+1）
          └── dashboard:recent_actions（最近50条）
```

### 4.3 接口缓存策略

```
第一次请求（缓存未命中）:
    请求 → RedisCacheUtil 查 Redis → 未命中 → 查 MySQL → 写 Redis(5min TTL) → 返回

后续请求（缓存命中）:
    请求 → RedisCacheUtil 查 Redis → 命中 → 直接返回(≈1ms)

缓存过期（5分钟后）:
    回到"第一次请求"流程
```

### 4.4 推荐查询链路

```
GET /api/recommend/personalized/{userId}?limit=20
    ↓
① 查 Redis rec:{uid} → 命中则返回视频 ID 列表
    ↓（未命中）
② 查 MySQL recommend_results → 回写 Redis → 返回结果
    ↓
③ 按 Redis 实时画像做分类分散（diversifyByCategory）
    ↓
④ 返回 Top 20 视频给前端
```

### 4.5 Hive 备份链路

```
VM crontab (每日 03:00):
  MySQL 增量 → HDFS → ODS 分区 → DWD 分区（Parquet 列存）

灾难恢复:
  DWD 表 → Sqoop export → MySQL user_behavior（恢复数据）
```

---

## 5. API 接口文档

Base URL: `http://localhost:8080/api`

### 5.1 推荐接口

| 方法 | 路径 | 说明 | 前端调用 |
|------|------|------|---------|
| GET | `/recommend/hot?limit=20` | 热门推荐 | ✅ HotVideos.vue |
| GET | `/recommend/personalized/{userId}?limit=20` | 个性化推荐 | ✅ RecommendVideos.vue |
| GET | `/recommend/category/{category}?limit=20` | 分类推荐 | ❌ 无前端调用 |

### 5.2 行为记录接口

| 方法 | 路径 | 说明 | 前端调用 |
|------|------|------|---------|
| POST | `/behavior` | 记录用户行为(点赞/观看) | ✅ RecommendVideos.vue |
| GET | `/behavior/user/{userId}` | 查询用户行为历史 | ❌ 无前端调用 |

### 5.3 大屏统计接口

#### 全局模式（全部带 Redis 缓存，5 分钟 TTL）

| 方法 | 路径 | 缓存 Key | 数据源 |
|------|------|----------|--------|
| GET | `/dashboard/summary` | `dashboard:summary` | MySQL → Redis |
| GET | `/dashboard/category-dist` | `dashboard:category-dist` | MySQL → Redis |
| GET | `/dashboard/activity-dist` | `dashboard:activity-dist` | MySQL → Redis |
| GET | `/dashboard/behavior-stats` | `dashboard:behavior-stats` | MySQL → Redis |
| GET | `/dashboard/hourly-trend` | `dashboard:hourly-trend` | MySQL → Redis |
| GET | `/dashboard/recommend-overview` | `dashboard:recommend-overview` | MySQL → Redis |
| GET | `/dashboard/realtime` | 无（实时数据） | Redis 直读 |

#### 用户模式

| 方法 | 路径 | 缓存 Key | 数据源 |
|------|------|----------|--------|
| GET | `/dashboard/user/{userId}/summary` | `dashboard:user:{id}:summary` | MySQL → Redis |
| GET | `/dashboard/user/{userId}/category-dist` | `dashboard:user:{id}:category-dist` | MySQL → Redis |
| GET | `/dashboard/user/{userId}/behavior-stats` | `dashboard:user:{id}:behavior-stats` | MySQL → Redis |
| GET | `/dashboard/user/{userId}/hourly-trend` | `dashboard:user:{id}:hourly-trend` | MySQL → Redis |
| GET | `/dashboard/user-interest/{userId}` | 无（直读 Redis Hash） | Redis → MySQL |

---

## 6. 数据库设计

### 6.1 MySQL 表结构

```sql
-- 用户表（ColdStartApp 首次写入）
CREATE TABLE users (
  id BIGINT PRIMARY KEY,
  username VARCHAR(50)
);

-- 视频表（ColdStartApp 首次写入）
CREATE TABLE videos (
  id BIGINT PRIMARY KEY,
  category VARCHAR(50),
  tags VARCHAR(500),
  duration INT DEFAULT 0,
  view_count INT DEFAULT 0
);

-- 用户行为表（冷启动写入基准 + 前端持续写入新数据）
CREATE TABLE user_behavior (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  video_category VARCHAR(50),
  like_type INT DEFAULT 0,
  relay_type INT DEFAULT 0,
  viewing_time DOUBLE DEFAULT 0,
  behavior_time DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_category_stats (video_category, viewing_time, like_type, relay_type),
  INDEX idx_hourly (behavior_time, user_id),
  INDEX idx_user_stats (user_id, video_category, viewing_time, like_type)
);

-- 用户画像表（ColdStartApp 写入）
CREATE TABLE user_profile (
  user_id BIGINT PRIMARY KEY,
  interest_tags VARCHAR(500),
  avg_viewing_time DOUBLE DEFAULT 0,
  total_watch_count INT DEFAULT 0,
  like_rate DOUBLE DEFAULT 0,
  active_level INT DEFAULT 1,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 推荐结果表（ColdStartApp 写入）
CREATE TABLE recommend_results (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  video_ids TEXT NOT NULL,
  strategy VARCHAR(20) DEFAULT 'ALS',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_id (user_id)
);
```

### 6.2 Redis Key 设计

| Key 模式 | 类型 | TTL | 用途 | 写入方 |
|----------|------|-----|------|--------|
| `rec:{userId}` | String | 24h | ALS 推荐视频 ID 列表 | ColdStartApp |
| `profile:{userId}:cats` | Hash | 24h | 用户分类偏好计数 | ColdStartApp + StreamingApp |
| `profile:{userId}:stats` | Hash | 24h | 用户统计 | ColdStartApp + StreamingApp |
| `dashboard:{*}` | String | 5min | 大屏接口缓存 | RedisCacheUtil |
| `dashboard:total_behaviors` | String | 无 | 全局行为计数 | StreamingApp |
| `dashboard:category_counts` | ZSet | 无 | 分类行为热度 | StreamingApp |
| `dashboard:recent_actions` | List | 无(ltrim 50) | 最近 50 条行为动态 | StreamingApp |

---

## 7. 前端大屏

### 7.1 路由

```
/          → 重定向到 /dashboard
/dashboard → DashboardPage.vue（大屏主页）
```

### 7.2 组件结构

```
DashboardPage.vue
├── DashboardSkeleton.vue        — 加载骨架屏
├── KpiCards.vue                 — 全局 KPI 卡片
├── UserKpiCards.vue             — 用户 KPI 卡片
├── CategoryRoseChart.vue        — 分类分布玫瑰图
├── InteractionBarChart.vue      — 分类互动率柱状图
├── ActivityPieChart.vue         — 用户活跃等级饼图
├── AvgViewTimeChart.vue         — 平均观看时长柱状图
├── HourlyTrendChart.vue         — 小时行为趋势折线图
├── RealtimeActions.vue          — 实时行为动态滚动列表
├── OverviewRingChart.vue        — 推荐覆盖概览环形图
├── InterestTags.vue             — 用户兴趣标签
├── RecommendVideos.vue          — 个性化推荐视频列表
└── HotVideos.vue                — 热门视频列表
```

---

## 8. 大数据模块

### 8.1 ColdStartApp（冷启动）

位置：`big-data/src/main/scala/com/video/ColdStartApp.scala`

功能：从 MySQL user_behavior 表读取行为数据 → ALS 协同过滤训练 → 用户画像计算 → 写入 MySQL + Redis

运行方式（本地 IDEA 或命令行）：

```bash
# 首次运行（覆盖 MySQL 和 Redis）
cd big-data
mvn exec:java -Dexec.mainClass=com.video.ColdStartApp -Dexec.args="--overwrite-behavior"

# 增量运行（保留已有数据，刷新推荐和 Redis 缓存）
mvn exec:java -Dexec.mainClass=com.video.ColdStartApp
```

ALS 参数：rank=5, maxIter=10, regParam=0.1，推荐 Top 50/用户

### 8.2 StreamingApp（实时流处理）

位置：`big-data/src/main/scala/com/video/streaming/StreamingApp.scala`

功能：消费 Kafka `user_behavior` 消息 → 实时更新 Redis 画像 + 大屏统计

---

## 9. 启动方式

### 9.1 后端

```bash
cd video-recommend
mvnw spring-boot:run
```

后端启动后会自动预热慢查询（behavior-stats / hourly-trend），预热完成后大屏秒开。

### 9.2 前端

```bash
cd video-front
npm install   # 首次
npm run dev   # 开发模式 http://localhost:5173
```

### 9.3 环境依赖

| 服务 | 地址 | 用途 |
|------|------|------|
| MySQL | 192.168.126.130:3306 | 业务数据库 |
| Redis | 192.168.126.130:6379 | 缓存层 |
| Kafka | 192.168.126.130:9092 | 消息队列（可选） |

### 9.4 启动顺序

```
1. 确保 VM 上 MySQL/Redis 正常运行
2. （可选）先运行 ColdStartApp 刷新推荐数据
3. 启动 Spring Boot 后端（自动预热缓存）
4. 启动 Vue 前端
```

---

## 10. VM 运维管理

### 10.1 服务节点

| 服务 | 地址 | 用途 |
|------|------|------|
| MySQL | 192.168.126.130:3306 | 业务数据库 |
| Redis | 192.168.126.130:6379 | 缓存层 |
| HDFS | 192.168.126.130:8020 | 数据备份存储 |
| Hive Metastore | 192.168.126.132:9083 | 元数据服务 |
| Hive Server | 192.168.126.133 | ETL 计算节点 |

### 10.2 脚本文件

位于 `big-data/scripts/` 目录，部署在 VM 的 `/usr/script/` 下：

| 脚本 | 说明 | 运行方式 |
|------|------|---------|
| `hive_ddl.sql` | Hive 备份表 DDL（ODS + DWD） | `hive -f hive_ddl.sql` |
| `etl_init.sh` | 首次全量初始化（HDFS → ODS → DWD → MySQL） | `bash etl_init.sh` |
| `etl_daily.sh` | 每日增量备份（MySQL → ODS → DWD） | crontab 自动 |

### 10.3 Hive 备份架构

```
HDFS CSV → ODS（原始数据层，外部表，CSV 格式）
              ↓
          DWD（清洗明细层，Parquet 列存，按 dt 分区）
              ↓
          Sqoop export → MySQL user_behavior（灾难恢复）
```

ODS 和 DWD 表按日期分区（`dt=yyyy-MM-dd`），每日增量数据自动归档到 Hive。

### 10.4 crontab 自动调度

VM 133 上已配置 crontab，每日 03:00 自动执行 `etl_daily.sh`：

```crontab
0 3 * * * /usr/script/etl_daily.sh
```

`etl_daily.sh` 执行流程：
1. **03:00**: Sqoop 导出 MySQL 昨日增量数据到 HDFS
2. **03:10**: 加载 ODS 分区 + ODS → DWD 清洗
3. **完成后**: 在本地运行 ColdStartApp 更新推荐

### 10.5 灾难恢复

MySQL 数据丢失时：

```bash
# 1. 在 VM 上重新执行 etl_init.sh（恢复 user_behavior 数据）
bash etl_init.sh

# 2. 在本地执行 ColdStartApp（恢复推荐 + Redis 缓存）
cd big-data
mvn exec:java -Dexec.mainClass=com.video.ColdStartApp -Dexec.args="--overwrite-behavior"
```


