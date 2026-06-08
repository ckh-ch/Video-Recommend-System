package org.example.videorecommend.controller;
import org.example.videorecommend.entity.*;
import org.example.videorecommend.mapper.DashboardMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired private DashboardMapper dashboardMapper;
    @Autowired(required = false) private StringRedisTemplate redisTemplate;
    @Autowired private ObjectMapper objectMapper;

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
    public Object behaviorStats() {
        if (redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get("dashboard:behavior_stats");
                if (json != null) {
                    return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
                }
            } catch (Exception e) { /* fall through to MySQL */ }
        }
        return dashboardMapper.behaviorStats();
    }

    @GetMapping("/hourly-trend")
    public Object hourlyTrend() {
        if (redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get("dashboard:hourly_trend");
                if (json != null) {
                    return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
                }
            } catch (Exception e) { /* fall through to MySQL */ }
        }
        return dashboardMapper.hourlyTrend();
    }

    @GetMapping("/recommend-overview")
    public Map<String, Object> recommendOverview() {
        Long totalUsers = dashboardMapper.countUsers();
        Long totalRecommends = dashboardMapper.countRecommends();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("totalRecommends", totalRecommends);
        result.put("coverage", totalUsers > 0 ? (double) totalRecommends / totalUsers : 0);
        return result;
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

    @GetMapping("/user-interest/{userId}")
    public Map<String, Object> userInterest(@PathVariable Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        // 优先从 Redis 读取 (ColdStartApp 全量写入)
        if (redisTemplate != null) {
            try {
                String key = "profile:" + userId + ":cats";
                Map<Object, Object> cats = redisTemplate.opsForHash().entries(key);
                if (cats != null && !cats.isEmpty()) {
                    List<Map<String, Object>> tags = new ArrayList<>();
                    for (Map.Entry<Object, Object> e : cats.entrySet()) {
                        Map<String, Object> tag = new LinkedHashMap<>();
                        tag.put("name", e.getKey().toString());
                        tag.put("count", Long.parseLong(e.getValue().toString()));
                        tags.add(tag);
                    }
                    tags.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));
                    result.put("tags", tags);
                    return result;
                }
            } catch (Exception e) { /* fall through to MySQL */ }
        }
        // Redis 无数据则查 MySQL 兜底
        try {
            List<CategoryDist> dbTags = dashboardMapper.userCategoryInterests(userId);
            List<Map<String, Object>> tags = new ArrayList<>();
            if (dbTags != null) {
                for (CategoryDist d : dbTags) {
                    Map<String, Object> tag = new LinkedHashMap<>();
                    tag.put("name", d.getName());
                    tag.put("count", d.getValue());
                    tags.add(tag);
                }
            }
            result.put("tags", tags);
        } catch (Exception e) {
            result.put("tags", Collections.emptyList());
        }
        return result;
    }

    @GetMapping("/user/{userId}/summary")
    public Map<String, Object> userSummary(@PathVariable Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalBehaviors", dashboardMapper.countUserBehaviors(userId));
        result.put("totalCategories", dashboardMapper.countUserCategories(userId));
        result.put("totalViewTime", dashboardMapper.sumUserViewTime(userId));
        result.put("totalLikes", dashboardMapper.sumUserLikes(userId));
        return result;
    }

    @GetMapping("/user/{userId}/category-dist")
    public List<CategoryDist> userCategoryDist(@PathVariable Long userId) {
        return dashboardMapper.userCategoryDistribution(userId);
    }

    @GetMapping("/user/{userId}/behavior-stats")
    public List<BehaviorStat> userBehaviorStats(@PathVariable Long userId) {
        return dashboardMapper.userBehaviorStats(userId);
    }

    @GetMapping("/user/{userId}/hourly-trend")
    public List<HourlyTrend> userHourlyTrend(@PathVariable Long userId) {
        return dashboardMapper.userHourlyTrend(userId);
    }
}
