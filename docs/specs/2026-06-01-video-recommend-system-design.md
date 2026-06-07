# 短视频推荐系统 — 设计文档

## 1. 概述

基于现有短视频行为数据集（dy_action_view.csv）构建一个完整的短视频推荐系统，包含用户画像构建、协同过滤推荐、实时行为采集、前端展示等核心功能。

### 技术栈

| 模块 | 技术 | 版本 | 运行位置 |
|------|------|------|---------|
| 前端 | Vue 3 + Vite | 3.5.x / 8.x | 本地开发机 |
| 后端 API | Spring Boot + MyBatis | 4.0.6 / 4.0.1 | 本地开发机 |
| 大数据计算 | Spark MLlib (local 模式) | 3.5.4 | 本地开发机 |
| 数据库 | MySQL | - | VM 192.168.126.130:3306 |
| 缓存 | Redis | - | VM 192.168.126.130:6379 |
| 消息队列 | Kafka | - | VM 192.168.126.130:9092 |
| 文件存储 | HDFS | - | VM 192.168.126.130:9000 |

### 项目模块

- `video-recommend/` — Spring Boot 后端 API（Java 17）
- `video-front/` — Vue 3 前端页面
- `big-data/` — Spark 大数据处理（Scala 2.12, Java 8）

---

## 2. 架构总览

```
┌─────────────────────────────────────────────────────────────────────┐
│                          本地开发机 (Windows)                         │
│                                                                     │
│  ┌──────────────┐  REST API  ┌──────────────────────────────────┐  │
│  │  video-front │◄──────────►│      video-recommend             │  │
│  │  (Vue 3)     │  ①请求/响应  │      (Spring Boot + Java 17)    │  │
│  │              │            │                                  │  │
│  │  - 推荐页     │            │  Controller → Service → Mapper    │  │
│  │  - 搜索页     │            │  → MySQL JDBC → Redis Template    │  │
│  │  - 个人画像   │            │  → Kafka Producer                 │  │
│  └──────────────┘            └──────────────┬───────────────────┘  │
│                                             │                      │
│  ┌──────────────────────────────────────────┘                      │
│  │  big-data (Spark local 模式)                                    │
│  │                                                                 │
│  │  ColdStartApp(冷启动) ← HDFS(dy_action_view.csv)                 │
│  │     ├── ETL 清洗 → Spark DataFrame                              │
│  │     ├── ALS 协同过滤训练 → 推荐结果                              │
│  │     ├── 用户画像计算 → user_profile                             │
│  │     └── MySQL + Redis 写入                                       │
│  │                                                                 │
│  │  StreamingApp(常驻流处理) ← Kafka                               │
│  │     └── 消费行为 → 实时更新 Redis 画像                          │
│  └─────────────────────────────────────────────────────────────────┘
└─────────────────────────────────────────────────────────────────────┘
                           │
                    远程连接(JDBC/Jedis/Kafka Client/HDFS Client)
                           │
┌─────────────────────────────────────────────────────────────────────┐
│                VM 虚拟机 192.168.126.130                             │
│                                                                     │
│  ┌───────┐  ┌───────┐  ┌────────┐  ┌──────────┐                   │
│  │ HDFS  │  │ MySQL │  │ Redis  │  │  Kafka   │                   │
│  │ :9000 │  │ :3306 │  │ :6379  │  │ :9092    │                   │
│  └───────┘  └───────┘  └────────┘  └──────────┘                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. 数据流设计

### 3.1 冷启动（一次性）

```
dy_action_view.csv (本地 big-data/data/)
      │ scp / hdfs dfs -put
      ▼
VM HDFS /user/video-recommend/raw/dy_action_view.csv
      │
      ▼ (Spark 本地模式远程读取)
ColdStartApp.main()
      │
      ├─ 阶段1: Spark SQL 清洗
      │    - 去重 (user_id, video_id, time)
      │    - 过滤无效数据 (viewing_time <= 0)
      │    - 提取唯一用户列表 → MySQL users 表
      │    - 提取唯一视频列表 → MySQL videos 表
      │
      ├─ 阶段2: ALS 协同过滤训练
      │    输入: (userId_index, videoId_index, rating)
      │    rating = like_type * 2 + relay_type * 1 + viewing_time_norm * 0.5
      │    输出: 每个用户 Top-N 推荐视频 (含评分)
      │
      ├─ 阶段3: 用户画像计算
      │    - 按 video_category 统计用户偏好权重
      │    - 计算用户平均观看时长、点赞率
      │    - 划分活跃等级 (1-5)
      │    - 结果 → MySQL user_profile 表
      │
      └─ 阶段4: 结果写入
           - 推荐结果 → MySQL recommend_results 表
           - 推荐结果 → Redis (key: "rec:{userId}", TTL: 1天)
           - 用户画像 → MySQL user_profile 表
           - 用户画像 → Redis (key: "profile:{userId}", TTL: 1天)
```

### 3.2 日常运行（持续）

```
用户操作（观看/点赞/转发）
      │ POST /api/behavior
      ▼
video-recommend 收到请求
      │
      ├─ 写入 MySQL user_behavior 表（持久化）
      │
      └─ 发送 Kafka Topic "user_behavior"
          消息体:
          {
            "userId": 85500,
            "videoId": 834124,
            "action": "view",
            "likeType": 1,
            "relayType": 0,
            "viewingTime": 720.0,
            "category": "food",
            "timestamp": "2024-07-01T00:00:42"
          }
           │
           ▼
big-data StreamingApp (Spark Streaming, 常驻进程)
           │
           └─ 消费 Kafka 消息（每 5 秒微批）
                │
                ├─ 实时统计: 更新 Redis 中的画像计数
                │   Redis.hincrBy("profile:85500:cats", "food", 1)
                │   Redis.hincrBy("profile:85500:stats", "totalWatch", 1)
                │
                └─ 定期持久化到 MySQL (每 5 分钟)
                    UPSERT INTO user_profile SET ...
```

### 3.3 推荐查询链路

```
用户打开推荐页 → GET /api/recommend/personalized/{userId}?limit=20
                              │
                              ▼
                   video-recommend RecommendController
                              │
                              ▼
                   ① 查 Redis: get("rec:{userId}")
                          │
                    ┌──── YES ────┐ NO
                    ▼             ▼
              ② 返回结果    ① 查 MySQL: SELECT video_ids
                    │        FROM recommend_results WHERE user_id=?
                    │             │
                    │       ② 回写 Redis: set("rec:{userId}", ...)
                    │             │
                    └─── ③ 合并实时画像做重排序 ──┘
                                    │
                                    ▼
                             ④ 返回 Top 20 给前端
```

### 3.4 冷启动用户/新用户兜底

```
用户无画像/无推荐结果时:
  → 返回热门推荐 (Redis hot_videos / MySQL videos ORDER BY view_count DESC)
  → 用户产生行为后，走 Kafka → Spark Streaming → 逐渐产生个性化推荐
```

---

## 4. 数据库设计

### 4.1 MySQL 表结构

```sql
-- 用户表（冷启动时从 CSV 提取唯一用户）
CREATE TABLE users (
  id BIGINT PRIMARY KEY,
  username VARCHAR(50),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 视频表（冷启动时从 CSV 提取唯一视频）
CREATE TABLE videos (
  id BIGINT PRIMARY KEY,
  category VARCHAR(50),
  tags VARCHAR(500),
  duration INT DEFAULT 0,
  view_count INT DEFAULT 0
);

-- 用户行为表（日常运行时持续写入）
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
  INDEX idx_behavior_time (behavior_time)
);

-- 用户画像表（Spark 计算后写入）
CREATE TABLE user_profile (
  user_id BIGINT PRIMARY KEY,
  interest_tags VARCHAR(500),
  avg_viewing_time DOUBLE DEFAULT 0,
  total_watch_count INT DEFAULT 0,
  like_rate DOUBLE DEFAULT 0,
  active_level INT DEFAULT 1,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 推荐结果表（Spark ALS 计算后写入）
CREATE TABLE recommend_results (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  video_ids TEXT NOT NULL,
  strategy VARCHAR(20) DEFAULT 'ALS',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_id (user_id)
);
```

### 4.2 Redis Key 设计

| Key 模式 | 类型 | 用途 | TTL |
|----------|------|------|-----|
| `rec:{userId}` | String(JSON) | 用户推荐列表 | 1 天 |
| `profile:{userId}:cats` | Hash | 用户分类偏好计数 (field=category, value=count) | 7 天 |
| `profile:{userId}:stats` | Hash | 用户统计数据 (totalWatch, totalLike, totalViewTime) | 7 天 |
| `hot_videos` | String(JSON) | 热门视频列表 | 1 小时 |

### 4.3 Kafka Topic 设计

| Topic | 分区数 | 消息格式 | 用途 |
|-------|--------|---------|------|
| `user_behavior` | 3 | JSON | 用户行为实时上报 |

---

## 5. 推荐算法设计

### 5.1 ALS 协同过滤

```
输入特征: (userId, videoId, rating)

rating 计算公式:
  base_score = like_type * 2 + relay_type * 1
  duration_norm = viewing_time / avg_viewing_time_of_video  (归一化)
  final_rating = base_score + duration_norm * 0.5

模型参数:
  rank = 10        (隐特征数)
  iterations = 10   (迭代次数)
  lambda = 0.1      (正则化系数)

输出: 每个用户 Top 50 推荐视频
```

### 5.2 用户画像

```
基于用户历史行为统计:
  - 分类偏好: 按 video_category 分组计数，取 Top 5
  - 平均观看时长: AVG(viewing_time)
  - 点赞率: SUM(like_type) / COUNT(*)
  - 活跃等级:
      ≥100 次交互 → 5级
      ≥50         → 4级
      ≥20         → 3级
      ≥5          → 2级
      <5          → 1级
```

### 5.3 实时重排序

```
用户请求推荐时:
  1. 取 Redis 中的 ALS 推荐列表 (rec:{userId})
  2. 取 Redis 中的实时画像偏好 (profile:{userId}:cats)
  3. 如果用户近期偏好分类有变化，将该分类视频在列表中前置
  4. 再混合 20% 热门视频作为探索
```

---

## 6. API 接口设计

| 接口 | 方法 | 说明 |
|------|------|------|
| `GET /api/recommend/personalized/{userId}` | 查询 | 个性化推荐 |
| `GET /api/recommend/hot` | 查询 | 热门推荐 |
| `GET /api/recommend/category/{category}` | 查询 | 分类推荐 |
| `GET /api/videos/{id}` | 查询 | 视频详情 |
| `GET /api/videos/hot` | 查询 | 热门视频列表 |
| `GET /api/users/{id}/profile` | 查询 | 获取用户画像 |
| `POST /api/behavior` | 写入 | 记录用户行为 |

---

## 7. 开发阶段规划

### 阶段一：冷启动链路 (big-data + video-recommend 基础)

- [ ] 上传 CSV 到 HDFS
- [ ] Spark ColdStartApp: ETL + ALS + 画像 + 写MySQL/Redis
- [ ] video-recommend: 数据库表创建 + MyBatis Mapper + 基础查询 API
- [ ] MySQL 表结构初始化

### 阶段二：后端 API 完整实现

- [ ] 推荐接口 (个性化/热门/分类)
- [ ] 用户行为记录接口
- [ ] 用户画像查询接口
- [ ] Redis 缓存层集成
- [ ] Kafka 消息生产集成
- [ ] 实时重排序逻辑

### 阶段三：实时流处理

- [ ] Spark StreamingApp: 消费 Kafka → 更新 Redis
- [ ] Spark StreamingApp: 持久化到 MySQL
- [ ] 端到端联调: 行为 → Kafka → Redis → 推荐重排序

### 阶段四：前端展示

- [ ] 推荐页面 (视频卡片列表)
- [ ] 视频详情页
- [ ] 用户画像展示页
- [ ] 热门榜单页
- [ ] 用户行为交互 (点赞/收藏)

---

## 8. 非功能性设计

### 缓存策略
- 推荐结果缓存 1 天，用户量不大时可直接读 MySQL
- 热门视频缓存 1 小时，更新不频繁
- 实时画像 Hash 结构，支持字段级增量更新

### 错误处理
- 推荐接口返回空列表时，前端显示"暂无推荐，去看看热门"
- Redis 不可用时降级为直接读 MySQL
- Kafka 不可用时行为只写 MySQL，不阻塞用户

### 安全
- 本系统为内网项目，API 不做复杂鉴权
- 防止 SQL 注入：MyBatis 参数绑定
