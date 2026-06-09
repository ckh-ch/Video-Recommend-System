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

---

## 1. 项目概述

| 项目 | 说明 |
|------|------|
| 功能 | 短视频推荐 + 用户画像 + 可视化数据大屏 |
| 数据规模 | 约 198 万条用户行为数据，1000 用户，124947 视频 |
| 算法 | ALS 协同过滤 + 用户画像统计 |
| 大屏模式 | 全局数据大屏 / 用户画像大屏 双模式切换 |

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
| **文件存储** | HDFS | 3.x (VM 192.168.126.130:9000) |

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
│       │   ├── DashboardController.java    # 大屏统计接口（12个）
│       │   ├── RecommendController.java    # 推荐接口（3个）
│       │   └── UserBehaviorController.java # 行为记录接口（2个）
│       ├── mapper/
│       │   ├── DashboardMapper.java        # 大屏统计 SQL（17个方法）
│       │   ├── VideoMapper.java            # 视频查询（3个方法）
│       │   └── UserBehaviorMapper.java     # 行为写入查询（2个方法）
│       ├── service/
│       │   ├── RecommendService.java       # 推荐服务接口
│       │   └── impl/RecommendServiceImpl.java # 推荐实现（个性化/热门/分类）
│       ├── entity/                         # 实体类（8个）
│       ├── config/
│       │   └── CorsConfig.java             # CORS 跨域配置
│       └── resources/
│           ├── application.properties      # 应用配置
│           ├── schema.sql                  # 表结构
│           └── mapper/UserBehaviorMapper.xml
│
├── big-data/                       # Spark 大数据处理
│   └── src/main/scala/com/video/
│       ├── ColdStartApp.scala       # 冷启动：ALS 训练 + 画像计算 + 写 MySQL/Redis
│       └── streaming/StreamingApp.scala  # 实时流：消费 Kafka → 更新 Redis
│
└── docs/
    ├── etl/etl-design.md                    # ETL 数仓设计（计划中）
    └── plans/2026-06-09-system-optimization-plan.md  # 优化计划
```

---

## 4. 数据流设计

### 4.1 冷启动流程

```
HDFS 原始 CSV (dy_action_view.csv)
    ↓
ColdStartApp.scala
    ├── 阶段1: 读取清洗 → 去重/过滤
    ├── 阶段2: ALS 协同过滤训练 → user_idx, video_idx, rating
    ├── 阶段3: 用户画像计算 → avg_viewing_time, like_rate, active_level
    ├── 阶段4: 写入 MySQL (users/videos/user_behavior/recommend_results/user_profile)
    └── 阶段5: 写入 Redis (rec:{uid}/profile:{uid}:stats/profile:{uid}:cats/大屏统计)
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

### 4.3 推荐查询链路

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

### 4.4 用户无推荐降级

```
Redis rec:{uid} 和 MySQL recommend_results 均无数据
    ↓
降级为热门推荐: RecommendServiceImpl.getHotRecommend()
    ↓
返回 MySQL videos ORDER BY view_count DESC
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

#### 全局模式

| 方法 | 路径 | 说明 | 返回数据 | 数据源 |
|------|------|------|---------|--------|
| GET | `/dashboard/summary` | 全局 KPI | totalVideos, totalUsers, totalBehaviors, totalCategories | MySQL |
| GET | `/dashboard/category-dist` | 视频分类分布 | [{name, value}] | MySQL |
| GET | `/dashboard/activity-dist` | 用户活跃等级分布 | [{level, count}] | MySQL |
| GET | `/dashboard/behavior-stats` | 分类行为统计 | [{category, avgViewTime, likeRate, relayRate, behaviorCount}] | Redis → MySQL |
| GET | `/dashboard/hourly-trend` | 小时级趋势 | [{hour, count}] | Redis → MySQL |
| GET | `/dashboard/recommend-overview` | 推荐覆盖率 | totalUsers, totalRecommends, coverage | MySQL |
| GET | `/dashboard/realtime` | 实时动态 | totalBehaviors, categoryTop5, recentActions | Redis |

#### 用户模式

| 方法 | 路径 | 说明 | 返回数据 | 数据源 |
|------|------|------|---------|--------|
| GET | `/dashboard/user/{userId}/summary` | 用户 KPI | totalBehaviors, totalCategories, totalViewTime, totalLikes | MySQL |
| GET | `/dashboard/user/{userId}/category-dist` | 用户分类分布 | [{name, value}] | MySQL |
| GET | `/dashboard/user/{userId}/behavior-stats` | 用户行为统计 | [{category, avgViewTime, likeRate, relayRate, behaviorCount}] | MySQL |
| GET | `/dashboard/user/{userId}/hourly-trend` | 用户小时趋势 | [{hour, count}] | MySQL |
| GET | `/dashboard/user-interest/{userId}` | 用户兴趣标签 | {userId, tags: [{name, count}]} | Redis → MySQL |

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
  user_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  video_category VARCHAR(50),
  like_type INT DEFAULT 0,
  relay_type INT DEFAULT 0,
  viewing_time DOUBLE DEFAULT 0,
  behavior_time DATETIME,
  INDEX idx_user_id (user_id),
  INDEX idx_behavior_time (behavior_time)
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
| `profile:{userId}:stats` | Hash | 24h | 用户统计(avgViewTime/totalWatch/likeRate) | ColdStartApp + StreamingApp |
| `dashboard:behavior_stats` | String | 24h | 分类行为统计 JSON | ColdStartApp |
| `dashboard:hourly_trend` | String | 24h | 小时级趋势 JSON | ColdStartApp |
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
├── KpiCards.vue                 — 全局 KPI 卡片 (视频/用户/行为/分类)
├── UserKpiCards.vue             — 用户 KPI 卡片 (观看次数/分类/时长/点赞)
├── CategoryRoseChart.vue        — 分类分布玫瑰图
├── InteractionBarChart.vue      — 分类互动率柱状图
├── ActivityPieChart.vue         — 用户活跃等级饼图
├── AvgViewTimeChart.vue         — 平均观看时长横向柱状图
├── HourlyTrendChart.vue         — 小时行为趋势折线图
├── RealtimeActions.vue          — 实时行为动态滚动列表
├── OverviewRingChart.vue        — 推荐覆盖概览环形图
├── InterestTags.vue             — 用户兴趣标签
├── RecommendVideos.vue          — 个性化推荐视频列表
└── HotVideos.vue                — 热门视频列表
```

### 7.3 双模式布局

**全局模式：**
```
KPI(全局) → 3图(全局) → 平均时长(全局) → 热门视频 → 3图(全局)
```

**用户模式：**
```
KPI(用户) → 3图(用户) → 平均时长(用户) → 兴趣标签+推荐视频 → 3图(用户+全局)
```

### 7.4 API 调用关系

| 组件 | 调用的接口 | 模式 |
|------|-----------|------|
| DashboardPage | `/dashboard/summary`, `/dashboard/category-dist`, `/dashboard/activity-dist`, `/dashboard/behavior-stats`, `/dashboard/hourly-trend` | 全局 |
| DashboardPage | `/dashboard/user/{userId}/summary`, `/dashboard/user/{userId}/category-dist`, `/dashboard/user/{userId}/behavior-stats`, `/dashboard/user/{userId}/hourly-trend` | 用户 |
| OverviewRingChart | `/dashboard/recommend-overview` | 通用 |
| RealtimeActions | `/dashboard/realtime` | 通用 |
| InterestTags | `/dashboard/user-interest/{userId}` | 用户 |
| HotVideos | `/recommend/hot?limit=20` | 全局 |
| RecommendVideos | `/recommend/personalized/{userId}?limit=20`, `POST /behavior` | 用户 |
| UserKpiCards | 接收父组件传入的 summary | 用户 |

---

## 8. 大数据模块

### 8.1 ColdStartApp（冷启动）

位置：`big-data/src/main/scala/com/video/ColdStartApp.scala`

功能：从 HDFS 或 MySQL 读取行为数据 → ALS 协同过滤训练 → 用户画像计算 → 写入 MySQL + Redis

运行方式：

```bash
# 首次运行（初始化基准数据到 MySQL）
spark-submit --class com.video.ColdStartApp --master local[*] big-data-*.jar --overwrite-behavior

# 后续运行（增量，使用 MySQL 已有数据训练）
spark-submit --class com.video.ColdStartApp --master local[*] big-data-*.jar
```

ALS 参数：rank=10, maxIter=10, regParam=0.1，推荐 Top 50/用户

### 8.2 StreamingApp（实时流处理）

位置：`big-data/src/main/scala/com/video/streaming/StreamingApp.scala`

功能：消费 Kafka `user_behavior` 消息 → 实时更新 Redis 画像 + 大屏统计

运行方式：

```bash
spark-submit --class com.video.streaming.StreamingApp --master local[*] big-data-*.jar
```

---

## 9. 启动方式

### 9.1 后端

```bash
cd video-recommend
mvnw spring-boot:run
```

前端默认连接 `http://localhost:8080`。

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
| HDFS | 192.168.126.130:9000 | 原始数据存储 |

### 9.4 启动顺序

```
1. 确保 VM 上 MySQL/Redis/Kafka 正常运行
2. （可选）运行 ColdStartApp 初始化/刷新推荐数据
3. （可选）运行 StreamingApp 启动实时流处理
4. 启动 Spring Boot 后端
5. 启动 Vue 前端
```
