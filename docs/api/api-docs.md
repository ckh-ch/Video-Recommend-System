# 短视频推荐系统 - API 接口文档

**Base URL:** `http://localhost:8080/api`

---

## 目录

1. [热门推荐](#1-热门推荐)
2. [个性化推荐](#2-个性化推荐)
3. [分类推荐](#3-分类推荐)
4. [用户画像](#4-用户画像)
5. [视频热门列表](#5-视频热门列表)
6. [视频详情](#6-视频详情)
7. [分类视频列表](#7-分类视频列表)
8. [用户行为上报](#8-用户行为上报)
9. [用户行为查询](#9-用户行为查询)
10. [用户列表](#10-用户列表)

---

## 1. 热门推荐

获取全站热门视频列表。

**GET** `/recommend/hot`

### 请求参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `limit` | int | 否 | 20 | 返回数量 |

### 请求示例

```
GET /api/recommend/hot?limit=3
```

### 响应示例

```json
[
  {
    "id": 115555,
    "category": "movies",
    "tags": "movies",
    "duration": null,
    "viewCount": 0
  },
  {
    "id": 151511,
    "category": "amusement",
    "tags": "amusement",
    "duration": null,
    "viewCount": 0
  },
  {
    "id": 130296,
    "category": "finance",
    "tags": "finance",
    "duration": null,
    "viewCount": 0
  }
]
```

---

## 2. 个性化推荐

基于用户画像的个性化推荐。

**GET** `/recommend/personalized/{userId}`

### 请求参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `userId` | long | 是 | - | 用户 ID（路径参数） |
| `limit` | int | 否 | 20 | 返回数量 |

### 请求示例

```
GET /api/recommend/personalized/85500?limit=3
```

### 响应示例

```json
[
  {
    "id": 415781,
    "category": "news",
    "tags": "news",
    "duration": null,
    "viewCount": 0
  },
  {
    "id": 656341,
    "category": "pets",
    "tags": "pets",
    "duration": null,
    "viewCount": 0
  },
  {
    "id": 377058,
    "category": "food",
    "tags": "food",
    "duration": null,
    "viewCount": 0
  }
]
```

---

## 3. 分类推荐

按内容分类获取推荐视频。

**GET** `/recommend/category/{category}`

### 请求参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `category` | string | 是 | - | 视频分类（路径参数） |
| `limit` | int | 否 | 20 | 返回数量 |

### 请求示例

```
GET /api/recommend/category/food?limit=3
```

### 响应示例

```json
[
  {
    "id": 124545,
    "category": "food",
    "tags": "food",
    "duration": null,
    "viewCount": 0
  },
  {
    "id": 496656,
    "category": "food",
    "tags": "food",
    "duration": null,
    "viewCount": 0
  },
  {
    "id": 524052,
    "category": "food",
    "tags": "food",
    "duration": null,
    "viewCount": 0
  }
]
```

---

## 4. 用户画像

获取指定用户的画像数据。

**GET** `/users/{id}/profile`

### 请求参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `id` | long | 是 | - | 用户 ID（路径参数） |

### 请求示例

```
GET /api/users/85500/profile
```

### 响应示例

```json
{
  "userId": 85500,
  "interestTags": null,
  "avgViewingTime": 1090.25,
  "totalWatchCount": 1842,
  "likeRate": 0.48,
  "activeLevel": 5,
  "updateTime": null
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | long | 用户 ID |
| `interestTags` | string | 兴趣标签（逗号分隔） |
| `avgViewingTime` | double | 平均观看时长（秒） |
| `totalWatchCount` | int | 累计观看次数 |
| `likeRate` | double | 点赞率（0~1） |
| `activeLevel` | int | 活跃等级（1~5） |

---

## 5. 视频热门列表

按播放量排序获取热门视频。

**GET** `/videos/hot`

### 请求参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `limit` | int | 否 | 10 | 返回数量 |

### 请求示例

```
GET /api/videos/hot?limit=3
```

### 响应示例

```json
[
  {
    "id": 115555,
    "category": "movies",
    "tags": "movies",
    "duration": null,
    "viewCount": 0
  }
]
```

---

## 6. 视频详情

获取单个视频的详细信息。

**GET** `/videos/{id}`

### 请求参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `id` | long | 是 | - | 视频 ID（路径参数） |

### 请求示例

```
GET /api/videos/834124
```

### 响应示例

```json
{
  "id": 834124,
  "category": "food",
  "tags": "food",
  "duration": null,
  "viewCount": 0
}
```

---

## 7. 分类视频列表

按分类获取视频列表。

**GET** `/videos/category/{category}`

### 请求参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `category` | string | 是 | - | 视频分类（路径参数） |
| `limit` | int | 否 | 10 | 返回数量 |

### 请求示例

```
GET /api/videos/category/musics?limit=3
```

### 响应示例

```json
[
  { "id": 506050, "category": "musics", "tags": "musics", "duration": null, "viewCount": 0 }
]
```

---

## 8. 用户行为上报

记录用户行为（观看/点赞/转发），并同步发送到 Kafka 供实时流处理。

**POST** `/behavior`

### 请求参数（JSON Body）

| 字段 | 类型 | 必需 | 说明 |
|------|------|:----:|------|
| `userId` | long | 是 | 用户 ID |
| `videoId` | long | 是 | 视频 ID |
| `videoCategory` | string | 否 | 视频分类 |
| `likeType` | int | 否 | 是否点赞（1=是，0=否） |
| `relayType` | int | 否 | 是否转发（1=是，0=否） |
| `viewingTime` | double | 否 | 观看时长（秒） |

### 请求示例

```
POST /api/behavior
Content-Type: application/json

{
  "userId": 85500,
  "videoId": 834124,
  "videoCategory": "food",
  "likeType": 1,
  "relayType": 0,
  "viewingTime": 720.0
}
```

### 响应示例（成功）

```
ok
```

### 数据流向

```
POST /api/behavior → MySQL (持久化) → Kafka (user_behavior topic) → Spark Streaming → Redis 实时更新
```

### Redis 更新结果验证

行为上报后，Redis 中更新的 key：

| Redis Key | 类型 | 更新内容 |
|-----------|------|---------|
| `profile:85500:stats` | Hash | `totalWatch` +1，`totalLike` +1（如点赞），`totalViewTime` +观看时长 |
| `profile:85500:cats` | Hash | `food` 分类计数 +1 |

查询命令：
```bash
redis-cli -h 192.168.126.130 hgetall profile:85500:stats
redis-cli -h 192.168.126.130 hgetall profile:85500:cats
```

---

## 9. 用户行为查询

查询指定用户的历史行为记录。

**GET** `/behavior/user/{userId}`

### 请求参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `userId` | long | 是 | - | 用户 ID（路径参数） |

### 请求示例

```
GET /api/behavior/user/85500
```

### 响应示例

```json
[
  {
    "id": 1,
    "userId": 85500,
    "videoId": 834124,
    "videoCategory": "food",
    "likeType": 1,
    "relayType": 0,
    "viewingTime": 720.0,
    "behaviorTime": "2026-06-01T19:00:00",
    "createdAt": null
  }
]
```

---

## 10. 用户列表

获取所有用户。

**GET** `/users`

### 请求示例

```
GET /api/users
```

### 响应示例

```json
[
  { "id": 85500, "username": "user_85500" },
  { "id": 85501, "username": "user_85501" }
]
```

---

## 通用说明

### 数据模型

```json
Video:  { "id": long, "category": string, "tags": string, "duration": int, "viewCount": int }
UserProfile: { "userId": long, "interestTags": string, "avgViewingTime": double,
               "totalWatchCount": int, "likeRate": double, "activeLevel": int }
Behavior: { "userId": long, "videoId": long, "videoCategory": string,
            "likeType": int, "relayType": int, "viewingTime": double }
```

### 已知分类列表

| 分类 | 说明 |
|------|------|
| food | 美食 |
| tourism | 旅游 |
| amusement | 娱乐 |
| musics | 音乐 |
| daily life | 生活 |
| fashion | 时尚 |
| movies | 影视 |
| cosmetics | 美妆 |
| finance | 财经 |
| technology | 科技 |
| news | 新闻 |
| games | 游戏 |
| education | 教育 |
| pets | 宠物 |
| health | 健康 |
| sports | 体育 |
| anime | 动漫 |
| car | 汽车 |

### 测试脚本

```powershell
# API 测试（需要后端运行中）
.\scripts\test_api.ps1

# Redis 实时更新测试（需要后端 + StreamingApp 运行中）
.\scripts\test_redis.ps1
```

### 数据流概览

```
用户行为 → POST /api/behavior → MySQL (持久化)
                               → Kafka (user_behavior topic)
                               → Spark Streaming
                               → Redis (实时更新 profile:userId:stats)
                                     → 个性化推荐 API 读取实时画像做重排序

冷启动: Spark ALS → MySQL (videos/users/recommend_results)
                 → Redis (rec:userId 推荐列表缓存)
                 → 推荐 API 优先读取 Redis 缓存
```

### 接口状态汇总

| 接口 | 方法 | 状态 |
|------|------|:----:|
| `/api/recommend/hot` | GET | ✅ 已测试通过 |
| `/api/recommend/personalized/{userId}` | GET | ✅ 已测试通过 |
| `/api/recommend/category/{category}` | GET | ✅ 已测试通过 |
| `/api/users/{id}/profile` | GET | ✅ 已测试通过 |
| `/api/videos/hot` | GET | ✅ 已测试通过 |
| `/api/videos/{id}` | GET | ✅ 已测试通过 |
| `/api/videos/category/{category}` | GET | ✅ 已测试通过 |
| `/api/behavior` | POST | ✅ 已测试通过（含 Kafka+Redis 链路） |
| `/api/behavior/user/{userId}` | GET | ✅ 已测试通过 |
| `/api/users` | GET | ✅ 已测试通过 |
