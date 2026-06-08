# 可视化智慧大屏 — 设计文档

## 1. 概述

将原有前端页面重构为全屏可视化智慧大屏，充分利用现有行为数据集（dy_action_view.csv）与后端数据，以仪表盘形式直观展示视频推荐系统的核心指标、用户行为分布及实时动态。

### 范围

- 新增后端统计接口（DashboardController + DashboardMapper）
- 修改 ColdStartApp，写入 user_behavior 历史数据
- 修改 StreamingApp，增加 Redis 全局实时统计
- 前端全部替换为可视化大屏单页（Vue 3 + ECharts）
- 清理无用脚手架组件

### 不变部分

- ALS 协同过滤训练逻辑不变
- users / videos / user_profile / recommend_results 的写入逻辑不变
- 原有推荐 API（/recommend/personalized、/recommend/hot 等）保留
- 路由结构保持（`/` 重定向到大屏，原页面路径保留可访问）

---

## 2. 数据库

### 2.1 现有表 — 无需改动

```sql
-- videos 表：已有数据，大屏用于分类分布统计
CREATE TABLE IF NOT EXISTS videos (
  id BIGINT PRIMARY KEY,
  category VARCHAR(50),
  tags VARCHAR(500),
  duration INT DEFAULT 0,
  view_count INT DEFAULT 0
);

-- user_profile 表：已有数据，大屏用于活跃度分布
CREATE TABLE IF NOT EXISTS user_profile (
  user_id BIGINT PRIMARY KEY,
  interest_tags VARCHAR(500),
  avg_viewing_time DOUBLE DEFAULT 0,
  total_watch_count INT DEFAULT 0,
  like_rate DOUBLE DEFAULT 0,
  active_level INT DEFAULT 1,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 2.2 已有表 — 需确认存在

```sql
-- user_behavior 表：CSV 原始行为数据会写入此表
-- 大屏的 behavior-stats、hourly-trend、summary 接口依赖此表
CREATE TABLE IF NOT EXISTS user_behavior (
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
```

---

## 3. Big Data 模块改动

### 3.1 ColdStartApp — 写入 user_behavior

**位置：** ColdStartApp.scala，在清洗完 raw DataFrame 之后、ALS 训练之前添加。

**逻辑：** 直接将清洗后的数据写入 MySQL `user_behavior` 表。

```
raw DataFrame 字段: user_id, video_id, video_category, like_type, relay_type, time, viewing_time
                         ↓
user_behavior 表字段: user_id, video_id, video_category, like_type, relay_type, behavior_time, viewing_time
                         ↓
写入模式: SaveMode.Overwrite
```

字段映射说明：

| raw 字段 | 表字段 |
|---------|--------|
| `user_id` | `user_id` |
| `video_id` | `video_id` |
| `video_category` | `video_category` |
| `like_type` | `like_type` |
| `relay_type` | `relay_type` |
| `time` → 重命名 | `behavior_time` |
| `viewing_time` | `viewing_time` |

### 3.2 StreamingApp — Redis 全局统计

**位置：** StreamingApp.scala，在 `foreachBatch` 内处理每条行为时同步更新。

**新增 Redis Key：**

| Key | 类型 | 操作 | 用途 |
|-----|------|------|------|
| `dashboard:total_behaviors` | String | `INCR` | 累计总行为数 |
| `dashboard:category_counts` | ZSet | `ZINCRBY category_counts 1 {category}` | 各分类行为量排名 |
| `dashboard:hourly_activity` | String | `SETEX hourly_activity {hour} 3600` | 当前小时活跃计数 |
| `dashboard:recent_actions` | List | `LPUSH + LTRIM 0 49` | 最近 50 条行为动态 |

其中 `recent_actions` 列表的元素格式为 JSON 字符串：

```json
{"userId":85500,"category":"food","action":"like","time":"2026-06-07 12:00:00"}
```

---

## 4. 后端新增接口

### 4.1 DashboardController

新增 `DashboardController.java`，路径前缀 `/api/dashboard`，包含 6 个 GET 接口。

#### 4.1.1 GET /api/dashboard/summary

返回全局概览数字。

```json
{
  "totalVideos": 1200,
  "totalUsers": 850,
  "totalBehaviors": 50000,
  "totalCategories": 12
}
```

SQL：

```sql
SELECT COUNT(*) FROM videos;
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM user_behavior;
SELECT COUNT(DISTINCT category) FROM videos;
```

#### 4.1.2 GET /api/dashboard/category-dist

各分类视频数量，用于玫瑰图。

```json
[
  {"name": "food", "value": 98},
  {"name": "tourism", "value": 85}
]
```

SQL：

```sql
SELECT category AS name, COUNT(*) AS value FROM videos GROUP BY category ORDER BY value DESC;
```

#### 4.1.3 GET /api/dashboard/activity-dist

用户活跃等级分布，用于饼图。

```json
[
  {"level": 5, "count": 120},
  {"level": 4, "count": 300},
  {"level": 3, "count": 250},
  {"level": 2, "count": 150},
  {"level": 1, "count": 30}
]
```

SQL：

```sql
SELECT active_level AS level, COUNT(*) AS count FROM user_profile GROUP BY active_level ORDER BY level DESC;
```

#### 4.1.4 GET /api/dashboard/behavior-stats

各分类的平均观看时长和互动率，用于柱状图。

```json
[
  {
    "category": "food",
    "avgViewTime": 1520.5,
    "likeRate": 0.35,
    "relayRate": 0.12,
    "behaviorCount": 4200
  }
]
```

SQL：

```sql
SELECT
  video_category AS category,
  AVG(viewing_time) AS avgViewTime,
  AVG(like_type) AS likeRate,
  AVG(relay_type) AS relayRate,
  COUNT(*) AS behaviorCount
FROM user_behavior
GROUP BY video_category
ORDER BY behaviorCount DESC;
```

#### 4.1.5 GET /api/dashboard/hourly-trend

小时级行为分布，用于折线图。

```json
[
  {"hour": 0, "count": 150},
  {"hour": 1, "count": 120}
]
```

SQL：

```sql
SELECT HOUR(behavior_time) AS hour, COUNT(*) AS count
FROM user_behavior
GROUP BY HOUR(behavior_time)
ORDER BY hour;
```

#### 4.1.6 GET /api/dashboard/realtime

Redis 中的实时统计数据。

```json
{
  "totalBehaviors": 52300,
  "categoryTop5": [
    {"name": "food", "value": 8500},
    {"name": "musics", "value": 7200}
  ],
  "recentActions": [
    {"userId": 85500, "category": "food", "action": "like", "time": "12:00:00"}
  ]
}
```

数据来源：

| 字段 | Redis Key | 命令 |
|------|-----------|------|
| `totalBehaviors` | `dashboard:total_behaviors` | GET |
| `categoryTop5` | `dashboard:category_counts` | ZREVRANGE 0 4 WITHSCORES |
| `recentActions` | `dashboard:recent_actions` | LRANGE 0 -1 |

### 4.2 DashboardMapper

新增 `DashboardMapper.java`，使用 MyBatis `@Select` 注解实现上述 5 个 SQL 查询，每个方法返回对应的实体或 Map 列表。

需要新增的返回类型实体：

- `CategoryDist` — name, value
- `ActivityDist` — level, count
- `BehaviorStat` — category, avgViewTime, likeRate, relayRate, behaviorCount
- `HourlyTrend` — hour, count

---

## 5. 前端大屏

### 5.1 技术选型

| 技术 | 用途 | 说明 |
|------|------|------|
| Vue 3 + Vite | 框架 | 已有 |
| ECharts 6 | 图表 | 已安装但未使用 |
| Axios | HTTP | 已有 |

### 5.2 页面结构

单页全屏模式，无导航栏，路由 `/` 重定向到大屏。

```
┌──────────────────────────────────────────────────────────────────┐
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                           │
│  │ 视频  │ │ 用户  │ │ 行为  │ │ 分类  │    ← 4 个 KPI 数字卡片    │
│  │ 1,200│ │ 850  │ │50,000│ │ 12   │                           │
│  └──────┘ └──────┘ └──────┘ └──────┘                           │
├──────────┬───────────────────────┬────────────────────────────┤
│          │                       │                            │
│ ① 分类   │  ② 分类互动率对比     │  ③ 用户活跃等级分布        │
│   分布    │     (柱状图)          │     (饼图)                 │
│  (玫瑰图)  │                       │                            │
│          │                       │                            │
├──────────┴───────────────────────┴────────────────────────────┤
│          ④ 各分类平均观看时长排行 (横向柱状图)                  │
├──────────┬───────────────────────┬────────────────────────────┤
│          │                       │                            │
│ ⑤ 小时   │  ⑥ 实时行为动态       │  ⑦ 推荐概览 (环形图)       │
│   行为趋势│     (滚动列表)        │ 或备用面板                 │
│  (折线图)  │                       │                            │
│          │                       │                            │
└──────────┴───────────────────────┴────────────────────────────┘
```

### 5.3 组件拆分

| 组件 | 对应面板 |
|------|---------|
| `KpiCards.vue` | 顶部 4 个数字卡片 |
| `CategoryRoseChart.vue` | ① 分类分布玫瑰图 |
| `InteractionBarChart.vue` | ② 分类互动率柱状图 |
| `ActivityPieChart.vue` | ③ 用户活跃等级饼图 |
| `AvgViewTimeChart.vue` | ④ 平均观看时长横向柱状图 |
| `HourlyTrendChart.vue` | ⑤ 小时行为趋势折线图 |
| `RealtimeActions.vue` | ⑥ 实时行为动态滚动列表 |
| `OverviewRingChart.vue` | ⑦ 推荐概览环形图 |

### 5.4 样式设计

- **背景色：** 深色主题（`#0f1923` 或 `#0a1628`），大屏标准配色
- **卡片：** 半透明玻璃质感，带边框发光效果
- **图表色板：** 明亮渐变色系（蓝/青/紫/粉/橙）
- **标题：** 白色小字，左上角带小色块标记
- **自适应：** flex 布局，100vw x 100vh 全屏填充

### 5.5 数据刷新策略

| 面板 | 刷新频率 | 说明 |
|------|---------|------|
| KPI 卡片 | 页面加载时 | 静态统计数据 |
| 分类分布 | 页面加载时 | 静态统计数据 |
| 活跃分布 | 页面加载时 | 静态统计数据 |
| 互动率对比 | 页面加载时 | 静态统计数据 |
| 观看时长排行 | 页面加载时 | 静态统计数据 |
| 小时趋势 | 页面加载时 | 静态统计数据 |
| 实时动态 | 每 5 秒轮询 | 从 `/api/dashboard/realtime` 获取 |
| 推荐概览 | 页面加载时 | 静态统计数据 |

### 5.6 路由调整

```js
const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: DashboardPage },    // 新增大屏路由
  // 保留原路由但不再在导航中暴露
  { path: '/recommend/:userId', component: RecommendPage },
  { path: '/hot', component: HotPage },
  { path: '/video/:id', component: VideoDetail },
  { path: '/profile/:userId', component: UserProfile }
]
```

`nav-bar` 保留但默认隐藏，或直接移除。可通过快捷键或右上角小按钮切换。

### 5.7 清理

删除以下无用脚手架文件：

- `src/components/HelloWorld.vue`
- `src/components/TheWelcome.vue`
- `src/components/WelcomeItem.vue`
- `src/components/icons/IconCommunity.vue`
- `src/components/icons/IconDocumentation.vue`
- `src/components/icons/IconEcosystem.vue`
- `src/components/icons/IconSupport.vue`
- `src/components/icons/IconTooling.vue`

---

## 6. 实现步骤

### 阶段一：Big Data 层改动

1. **ColdStartApp** — 新增写入 user_behavior 表
2. **StreamingApp** — 新增 Redis 全局统计

### 阶段二：后端新增接口

3. 创建 `DashboardMapper.java` + 返回实体
4. 创建 `DashboardController.java`（6 个接口）
5. 重启后端验证接口可用

### 阶段三：前端大屏

6. 创建 `DashboardPage.vue` 大屏主页面
7. 创建 8 个图表子组件
8. 更新路由配置
9. 清理无用组件
10. 调整 `index.html` 标题

### 阶段四：集成测试

11. 重新运行 ColdStartApp 灌入数据
12. 启动后端，验证所有统计接口
13. 启动前端，验证大屏展示效果
