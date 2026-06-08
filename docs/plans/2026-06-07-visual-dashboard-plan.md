# 可视化智慧大屏 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将原有前端页面重构为可视化智慧大屏，展示视频推荐系统的核心指标、用户行为分布及实时动态。

**Architecture:** 四层联动 — ColdStartApp 将 CSV 行为数据写入 MySQL user_behavior 表；StreamingApp 消费 Kafka 同时更新 Redis 全局统计；后端新增 DashboardController 提供 6 个聚合统计接口；前端使用 Vue 3 + ECharts 构建深色主题大屏展示。

**Tech Stack:** Scala 2.12 / Spark 3.5.4, Spring Boot 4.0.6 / Java 17 / MyBatis, Vue 3 / ECharts 6 / Vite, MySQL / Redis

---

## 文件结构

### 新增文件

| 文件 | 说明 |
|------|------|
| `video-recommend/.../entity/DashboardSummary.java` | 全局概览实体 |
| `video-recommend/.../entity/CategoryDist.java` | 分类分布实体 |
| `video-recommend/.../entity/ActivityDist.java` | 活跃等级实体 |
| `video-recommend/.../entity/BehaviorStat.java` | 行为统计实体 |
| `video-recommend/.../entity/HourlyTrend.java` | 小时趋势实体 |
| `video-recommend/.../entity/RealtimeData.java` | 实时数据实体 |
| `video-recommend/.../mapper/DashboardMapper.java` | 统计查询 Mapper |
| `video-recommend/.../controller/DashboardController.java` | 统计接口控制器 |
| `video-front/src/views/DashboardPage.vue` | 大屏主页面 |
| `video-front/src/components/dashboard/KpiCards.vue` | 顶部 KPI 数字卡片 |
| `video-front/src/components/dashboard/CategoryRoseChart.vue` | 分类分布玫瑰图 |
| `video-front/src/components/dashboard/InteractionBarChart.vue` | 分类互动率柱状图 |
| `video-front/src/components/dashboard/ActivityPieChart.vue` | 用户活跃等级饼图 |
| `video-front/src/components/dashboard/AvgViewTimeChart.vue` | 平均观看时长排行图 |
| `video-front/src/components/dashboard/HourlyTrendChart.vue` | 小时行为趋势折线图 |
| `video-front/src/components/dashboard/RealtimeActions.vue` | 实时行为动态列表 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `big-data/.../ColdStartApp.scala` | 新增写入 user_behavior 表 |
| `big-data/.../streaming/StreamingApp.scala` | 新增 Redis 全局统计 |
| `video-front/src/router/index.js` | 新增 `/dashboard` 路由，`/` 重定向 |
| `video-front/index.html` | 标题改为"视频推荐系统 · 数据大屏" |

### 删除文件

```
video-front/src/components/HelloWorld.vue
video-front/src/components/TheWelcome.vue
video-front/src/components/WelcomeItem.vue
video-front/src/components/icons/IconCommunity.vue
video-front/src/components/icons/IconDocumentation.vue
video-front/src/components/icons/IconEcosystem.vue
video-front/src/components/icons/IconSupport.vue
video-front/src/components/icons/IconTooling.vue
```

---

## 阶段一：Big Data 层改动

### Task 1: ColdStartApp — 写入 user_behavior 表

**Files:**
- Modify: `big-data/src/main/scala/com/video/ColdStartApp.scala:55-56`

- [ ] **Step 1: 在写入 videos 表之后添加 user_behavior 写入代码**

在 ColdStartApp.scala 第 55 行 `videosDF.write...jdbc(...)` 之后、第 57 行 `=== 阶段2: ALS ===` 之前插入：

```scala
    println("=== 写入原始行为数据到 user_behavior ===")
    val behaviorDF = raw.select(
      col("user_id"),
      col("video_id"),
      col("video_category"),
      col("like_type"),
      col("relay_type"),
      col("time").as("behavior_time"),
      col("viewing_time")
    )
    behaviorDF.write.mode(SaveMode.Overwrite)
      .option("batchsize", "1000")
      .jdbc(MYSQL_URL, "user_behavior", new java.util.Properties() {
        put("user", MYSQL_USER); put("password", MYSQL_PWD)
      })
    println(s"写入行为数: ${behaviorDF.count()}")
```

- [ ] **Step 2: 验证无语法错误**

Run: 在 IDEA 中确认 ColdStartApp.scala 无编译错误。

- [ ] **Step 3: Commit**

```bash
git add big-data/src/main/scala/com/video/ColdStartApp.scala
git commit -m "feat(coldstart): write raw behavior data to user_behavior table"
```

---

### Task 2: StreamingApp — Redis 全局统计

**Files:**
- Modify: `big-data/src/main/scala/com/video/streaming/StreamingApp.scala:45-53`

- [ ] **Step 1: 在 Redis 更新中新增全局统计**

修改 StreamingApp.scala 第 45-53 行，在 `jedis.hincrByFloat` 之后添加：

```scala
            val jedis = new Jedis(REDIS_HOST, REDIS_PORT)
            try {
              // 原有用户级统计
              jedis.hincrBy(s"profile:$uid:cats", category, 1)
              jedis.hincrBy(s"profile:$uid:stats", "totalWatch", 1)
              if (likeType == 1) jedis.hincrBy(s"profile:$uid:stats", "totalLike", 1)
              jedis.hincrByFloat(s"profile:$uid:stats", "totalViewTime", viewTime)

              // 新增全局统计
              jedis.incr("dashboard:total_behaviors")
              jedis.zincrby("dashboard:category_counts", 1.0, category)
              val now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())
              val actionJson = s"""{"userId":$uid,"category":"$category","action":"${if (likeType == 1) "like" else "view"}","time":"$now"}"""
              jedis.lpush("dashboard:recent_actions", actionJson)
              jedis.ltrim("dashboard:recent_actions", 0, 49)
            } finally {
              jedis.close()
            }
```

- [ ] **Step 2: 验证无语法错误**

在 IDEA 中确认 StreamingApp.scala 无编译错误。

- [ ] **Step 3: Commit**

```bash
git add big-data/src/main/scala/com/video/streaming/StreamingApp.scala
git commit -m "feat(streaming): add Redis global dashboard stats"
```

---

## 阶段二：后端统计接口

### Task 3: 创建 Dashboard 实体类

**Files:**
- Create: `video-recommend/src/main/java/org/example/videorecommend/entity/DashboardSummary.java`
- Create: `video-recommend/src/main/java/org/example/videorecommend/entity/CategoryDist.java`
- Create: `video-recommend/src/main/java/org/example/videorecommend/entity/ActivityDist.java`
- Create: `video-recommend/src/main/java/org/example/videorecommend/entity/BehaviorStat.java`
- Create: `video-recommend/src/main/java/org/example/videorecommend/entity/HourlyTrend.java`
- Create: `video-recommend/src/main/java/org/example/videorecommend/entity/RealtimeData.java`

- [ ] **Step 1: 创建 DashboardSummary.java**

```java
package org.example.videorecommend.entity;
import lombok.Data;
@Data
public class DashboardSummary {
    private Long totalVideos;
    private Long totalUsers;
    private Long totalBehaviors;
    private Long totalCategories;
}
```

- [ ] **Step 2: 创建 CategoryDist.java**

```java
package org.example.videorecommend.entity;
import lombok.Data;
@Data
public class CategoryDist {
    private String name;
    private Long value;
}
```

- [ ] **Step 3: 创建 ActivityDist.java**

```java
package org.example.videorecommend.entity;
import lombok.Data;
@Data
public class ActivityDist {
    private Integer level;
    private Long count;
}
```

- [ ] **Step 4: 创建 BehaviorStat.java**

```java
package org.example.videorecommend.entity;
import lombok.Data;
@Data
public class BehaviorStat {
    private String category;
    private Double avgViewTime;
    private Double likeRate;
    private Double relayRate;
    private Long behaviorCount;
}
```

- [ ] **Step 5: 创建 HourlyTrend.java**

```java
package org.example.videorecommend.entity;
import lombok.Data;
@Data
public class HourlyTrend {
    private Integer hour;
    private Long count;
}
```

- [ ] **Step 6: 创建 RealtimeData.java**

```java
package org.example.videorecommend.entity;
import lombok.Data;
import java.util.List;
import java.util.Map;
@Data
public class RealtimeData {
    private Long totalBehaviors;
    private List<Map<String, Object>> categoryTop5;
    private List<String> recentActions;
}
```

- [ ] **Step 7: Commit**

```bash
git add video-recommend/src/main/java/org/example/videorecommend/entity/DashboardSummary.java
git add video-recommend/src/main/java/org/example/videorecommend/entity/CategoryDist.java
git add video-recommend/src/main/java/org/example/videorecommend/entity/ActivityDist.java
git add video-recommend/src/main/java/org/example/videorecommend/entity/BehaviorStat.java
git add video-recommend/src/main/java/org/example/videorecommend/entity/HourlyTrend.java
git add video-recommend/src/main/java/org/example/videorecommend/entity/RealtimeData.java
git commit -m "feat(entity): add dashboard entity classes"
```

---

### Task 4: 创建 DashboardMapper

**Files:**
- Create: `video-recommend/src/main/java/org/example/videorecommend/mapper/DashboardMapper.java`

- [ ] **Step 1: 创建 DashboardMapper.java**

```java
package org.example.videorecommend.mapper;
import org.example.videorecommend.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM videos")
    Long countVideos();

    @Select("SELECT COUNT(*) FROM users")
    Long countUsers();

    @Select("SELECT COUNT(*) FROM user_behavior")
    Long countBehaviors();

    @Select("SELECT COUNT(DISTINCT category) FROM videos")
    Long countCategories();

    @Select("SELECT category AS name, COUNT(*) AS value FROM videos GROUP BY category ORDER BY value DESC")
    List<CategoryDist> categoryDistribution();

    @Select("SELECT active_level AS level, COUNT(*) AS count FROM user_profile GROUP BY active_level ORDER BY level DESC")
    List<ActivityDist> activityDistribution();

    @Select("SELECT video_category AS category, AVG(viewing_time) AS avgViewTime, AVG(like_type) AS likeRate, AVG(relay_type) AS relayRate, COUNT(*) AS behaviorCount FROM user_behavior GROUP BY video_category ORDER BY behaviorCount DESC")
    List<BehaviorStat> behaviorStats();

    @Select("SELECT HOUR(behavior_time) AS hour, COUNT(*) AS count FROM user_behavior GROUP BY HOUR(behavior_time) ORDER BY hour")
    List<HourlyTrend> hourlyTrend();
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl video-recommend -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add video-recommend/src/main/java/org/example/videorecommend/mapper/DashboardMapper.java
git commit -m "feat(mapper): add DashboardMapper with aggregation queries"
```

---

### Task 5: 创建 DashboardController

**Files:**
- Create: `video-recommend/src/main/java/org/example/videorecommend/controller/DashboardController.java`

- [ ] **Step 1: 创建 DashboardController.java**

```java
package org.example.videorecommend.controller;
import org.example.videorecommend.entity.*;
import org.example.videorecommend.mapper.DashboardMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired private DashboardMapper dashboardMapper;
    @Autowired(required = false) private StringRedisTemplate redisTemplate;

    @GetMapping("/summary")
    public DashboardSummary summary() {
        DashboardSummary s = new DashboardSummary();
        s.setTotalVideos(dashboardMapper.countVideos());
        s.setTotalUsers(dashboardMapper.countUsers());
        s.setTotalBehaviors(dashboardMapper.countBehaviors());
        s.setTotalCategories(dashboardMapper.countCategories());
        return s;
    }

    @GetMapping("/category-dist")
    public List<CategoryDist> categoryDist() {
        return dashboardMapper.categoryDistribution();
    }

    @GetMapping("/activity-dist")
    public List<ActivityDist> activityDist() {
        return dashboardMapper.activityDistribution();
    }

    @GetMapping("/behavior-stats")
    public List<BehaviorStat> behaviorStats() {
        return dashboardMapper.behaviorStats();
    }

    @GetMapping("/hourly-trend")
    public List<HourlyTrend> hourlyTrend() {
        return dashboardMapper.hourlyTrend();
    }

    @GetMapping("/realtime")
    public RealtimeData realtime() {
        RealtimeData r = new RealtimeData();
        if (redisTemplate == null) {
            r.setTotalBehaviors(0L);
            r.setCategoryTop5(Collections.emptyList());
            r.setRecentActions(Collections.emptyList());
            return r;
        }
        try {
            String total = redisTemplate.opsForValue().get("dashboard:total_behaviors");
            r.setTotalBehaviors(total != null ? Long.parseLong(total) : 0L);

            Set<String> top = redisTemplate.opsForZSet().reverseRange("dashboard:category_counts", 0, 4);
            List<Map<String, Object>> top5 = new ArrayList<>();
            if (top != null) {
                for (String cat : top) {
                    Double score = redisTemplate.opsForZSet().score("dashboard:category_counts", cat);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", cat);
                    item.put("value", score != null ? score.longValue() : 0);
                    top5.add(item);
                }
            }
            r.setCategoryTop5(top5);

            List<String> actions = redisTemplate.opsForList().range("dashboard:recent_actions", 0, -1);
            r.setRecentActions(actions != null ? actions : Collections.emptyList());
        } catch (Exception e) {
            r.setTotalBehaviors(0L);
            r.setCategoryTop5(Collections.emptyList());
            r.setRecentActions(Collections.emptyList());
        }
        return r;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl video-recommend -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add video-recommend/src/main/java/org/example/videorecommend/controller/DashboardController.java
git commit -m "feat(controller): add DashboardController with 6 statistic endpoints"
```

---

## 阶段三：前端大屏

### Task 6: 视觉设计 — ui-ux-pro-max

**Files:**
- 输出设计规范文档（配色、字体、卡片样式、图表色板）

- [ ] **Step 1: 调用 ui-ux-pro-max skill 进行设计咨询**

在 IDE 中调用 ui-ux-pro-max skill，输入以下上下文：

```
为视频推荐系统的可视化智慧大屏设计视觉方案。
- 深色主题大屏（类似 DataV 风格）
- 包含：4个KPI数字卡片、玫瑰图、饼图、柱状图、折线图、实时滚动列表
- 全屏展示，无导航栏
- 需要：主背景色、卡片样式、字体方案、图表色板
- 产品定位：B站风格视频推荐系统的运营数据看板
```

- [ ] **Step 2: 记录设计规范**

将 ui-ux-pro-max 输出的设计规范记录到设计文档中。

---

### Task 7: 前端组件实现 — frontend-design

**Files:**
- Create: `video-front/src/views/DashboardPage.vue`
- Create: `video-front/src/components/dashboard/KpiCards.vue`
- Create: `video-front/src/components/dashboard/CategoryRoseChart.vue`
- Create: `video-front/src/components/dashboard/InteractionBarChart.vue`
- Create: `video-front/src/components/dashboard/ActivityPieChart.vue`
- Create: `video-front/src/components/dashboard/AvgViewTimeChart.vue`
- Create: `video-front/src/components/dashboard/HourlyTrendChart.vue`
- Create: `video-front/src/components/dashboard/RealtimeActions.vue`
- Modify: `video-front/src/router/index.js`
- Modify: `video-front/index.html`

- [ ] **Step 1: 调用 frontend-design skill 实现大屏页面及组件**

在 IDE 中调用 frontend-design skill，传入以下上下文：

```
基于 Vue 3 + ECharts 6 + Vite 实现可视化智慧大屏。
设计规范参考 ui-ux-pro-max 的输出。

需要创建以下文件：

1. DashboardPage.vue - 主页面，100vw x 100vh 全屏，flex 布局
   - 顶部区域：KpiCards 组件
   - 中间三列：CategoryRoseChart / InteractionBarChart / ActivityPieChart
   - 第二行全宽：AvgViewTimeChart
   - 底部三列：HourlyTrendChart / RealtimeActions

2. KpiCards.vue - 4 个数字卡片（总视频数、总用户数、总行为数、总分类数）
   - props: summary 对象

3. CategoryRoseChart.vue - 南丁格尔玫瑰图
   - props: data (CategoryDist 数组)
   - API: GET /api/dashboard/category-dist

4. InteractionBarChart.vue - 分组柱状图（各分类点赞率+转发率）
   - props: data (BehaviorStat 数组)
   - API: GET /api/dashboard/behavior-stats

5. ActivityPieChart.vue - 环形饼图
   - props: data (ActivityDist 数组)
   - API: GET /api/dashboard/activity-dist

6. AvgViewTimeChart.vue - 横向柱状图
   - props: data (BehaviorStat 数组)
   - 取 avgViewTime 字段

7. HourlyTrendChart.vue - 折线图
   - props: data (HourlyTrend 数组)
   - API: GET /api/dashboard/hourly-trend

8. RealtimeActions.vue - 滚动列表
   - 每 5 秒轮询 GET /api/dashboard/realtime
   - 显示最近用户行为: "用户 {userId} {action}了 {category}类视频"

API 前缀：http://localhost:8080/api
数据刷新：静态面板页面加载时一次，RealtimeActions 每 5 秒轮询
```

- [ ] **Step 2: 清理无用脚手架组件**

```bash
git rm video-front/src/components/HelloWorld.vue
git rm video-front/src/components/TheWelcome.vue
git rm video-front/src/components/WelcomeItem.vue
git rm video-front/src/components/icons/IconCommunity.vue
git rm video-front/src/components/icons/IconDocumentation.vue
git rm video-front/src/components/icons/IconEcosystem.vue
git rm video-front/src/components/icons/IconSupport.vue
git rm video-front/src/components/icons/IconTooling.vue
```

- [ ] **Step 3: 修改 App.vue 隐藏导航栏和容器约束**

修改 `video-front/src/App.vue`，当路由为 `/dashboard` 时不显示导航栏和容器限制：

```vue
<template>
  <div id="app">
    <nav v-if="$route.path !== '/dashboard'" class="nav-bar">
      <router-link to="/recommend/85500">推荐</router-link>
      <router-link to="/hot">热门</router-link>
      <router-link to="/profile/85500">我的画像</router-link>
    </nav>
    <div v-if="$route.path !== '/dashboard'" class="container">
      <router-view />
    </div>
    <router-view v-else />
  </div>
</template>

<style>
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; }
.nav-bar { background: #fb7299; padding: 0 20px; display: flex; align-items: center; height: 48px; gap: 24px; }
.nav-bar a { color: white; text-decoration: none; font-size: 15px; opacity: 0.9; }
.nav-bar a:hover { opacity: 1; }
.container { max-width: 1200px; margin: 0 auto; padding: 20px; }
</style>
```

- [ ] **Step 4: 更新路由配置**

修改 `video-front/src/router/index.js`：

```js
import { createRouter, createWebHistory } from 'vue-router'
import DashboardPage from '../views/DashboardPage.vue'
import RecommendPage from '../views/RecommendPage.vue'
import HotPage from '../views/HotPage.vue'
import VideoDetail from '../views/VideoDetail.vue'
import UserProfile from '../views/UserProfile.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: DashboardPage },
  { path: '/recommend/:userId', component: RecommendPage },
  { path: '/hot', component: HotPage },
  { path: '/video/:id', component: VideoDetail },
  { path: '/profile/:userId', component: UserProfile }
]
```

- [ ] **Step 5: 更新页面标题**

修改 `video-front/index.html` 第 7 行：

```html
<title>视频推荐系统 · 数据大屏</title>
```

- [ ] **Step 6: Commit**

```bash
git add video-front/src/views/ video-front/src/components/dashboard/ video-front/src/router/ video-front/index.html
git rm video-front/src/components/HelloWorld.vue video-front/src/components/TheWelcome.vue video-front/src/components/WelcomeItem.vue video-front/src/components/icons/
git commit -m "feat(dashboard): add visual dashboard page with ECharts components"
```

---

## 阶段四：集成测试

### Task 8: 验证全链路

- [ ] **Step 1: 重新运行 ColdStartApp 灌入数据**

在 IDEA 中运行 ColdStartApp.main()，确认控制台输出：
- "有效行为数: N"
- "写入行为数: N"（新增）
- "=== 冷启动完成! ==="

验证 MySQL：
```sql
SELECT COUNT(*) FROM user_behavior;
SELECT COUNT(*) FROM videos;
SELECT COUNT(*) FROM users;
```
预期：均有数据

- [ ] **Step 2: 验证后端统计接口**

启动 Spring Boot，访问以下接口：
```bash
curl http://localhost:8080/api/dashboard/summary
curl http://localhost:8080/api/dashboard/category-dist
curl http://localhost:8080/api/dashboard/activity-dist
curl http://localhost:8080/api/dashboard/behavior-stats
curl http://localhost:8080/api/dashboard/hourly-trend
curl http://localhost:8080/api/dashboard/realtime
```
预期：均返回 JSON 数据，不报错

- [ ] **Step 3: 验证前端大屏**

启动前端：`npm run dev`
访问 `http://localhost:5173/dashboard`
预期：全屏大屏展示所有图表，KPI 卡片有数字，图表有数据，实时动态可滚动

- [ ] **Step 4: Commit**

```bash
git add .
git commit -m "test: verify full dashboard pipeline"
```
