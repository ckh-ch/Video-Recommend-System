package org.example.videorecommend.controller;

import tools.jackson.core.type.TypeReference;
import org.example.videorecommend.config.RedisCacheUtil;
import org.example.videorecommend.entity.*;
import org.example.videorecommend.mapper.DashboardMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired private DashboardMapper dashboardMapper;
    @Autowired(required = false) private StringRedisTemplate redisTemplate;
    @Autowired private RedisCacheUtil cacheUtil;

    private static final long CACHE_TTL = 300; // 5 分钟

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return cacheUtil.get("dashboard:summary", CACHE_TTL, DashboardSummary.class, () -> {
            DashboardSummary s = new DashboardSummary();
            s.setTotalVideos(dashboardMapper.countVideos());
            s.setTotalUsers(dashboardMapper.countUsers());
            s.setTotalBehaviors(dashboardMapper.countBehaviors());
            s.setTotalCategories(dashboardMapper.countCategories());
            return s;
        });
    }

    @GetMapping("/category-dist")
    public List<CategoryDist> categoryDist() {
        return cacheUtil.get("dashboard:category-dist", CACHE_TTL,
                new TypeReference<List<CategoryDist>>() {},
                dashboardMapper::categoryDistribution);
    }

    @GetMapping("/activity-dist")
    public List<ActivityDist> activityDist() {
        return cacheUtil.get("dashboard:activity-dist", CACHE_TTL,
                new TypeReference<List<ActivityDist>>() {},
                dashboardMapper::activityDistribution);
    }

    @GetMapping("/behavior-stats")
    public List<BehaviorStat> behaviorStats() {
        return cacheUtil.get("dashboard:behavior-stats", CACHE_TTL,
                new TypeReference<List<BehaviorStat>>() {},
                dashboardMapper::behaviorStats);
    }

    @GetMapping("/hourly-trend")
    public List<HourlyTrend> hourlyTrend() {
        return cacheUtil.get("dashboard:hourly-trend", CACHE_TTL,
                new TypeReference<List<HourlyTrend>>() {},
                dashboardMapper::hourlyTrend);
    }

    @GetMapping("/recommend-overview")
    public Map<String, Object> recommendOverview() {
        return cacheUtil.get("dashboard:recommend-overview", CACHE_TTL,
                new TypeReference<Map<String, Object>>() {}, () -> {
            Long totalUsers = dashboardMapper.countUsers();
            Long totalRecommends = dashboardMapper.countRecommends();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalUsers", totalUsers);
            result.put("totalRecommends", totalRecommends);
            result.put("coverage", totalUsers > 0 ? (double) totalRecommends / totalUsers : 0);
            return result;
        });
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
        String key = "dashboard:user:" + userId + ":summary";
        return cacheUtil.get(key, CACHE_TTL, new TypeReference<Map<String, Object>>() {}, () -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalBehaviors", dashboardMapper.countUserBehaviors(userId));
            result.put("totalCategories", dashboardMapper.countUserCategories(userId));
            result.put("totalViewTime", dashboardMapper.sumUserViewTime(userId));
            result.put("totalLikes", dashboardMapper.sumUserLikes(userId));
            return result;
        });
    }

    @GetMapping("/user/{userId}/category-dist")
    public List<CategoryDist> userCategoryDist(@PathVariable Long userId) {
        return cacheUtil.get("dashboard:user:" + userId + ":category-dist", CACHE_TTL,
                new TypeReference<List<CategoryDist>>() {},
                () -> dashboardMapper.userCategoryDistribution(userId));
    }

    @GetMapping("/user/{userId}/behavior-stats")
    public List<BehaviorStat> userBehaviorStats(@PathVariable Long userId) {
        return cacheUtil.get("dashboard:user:" + userId + ":behavior-stats", CACHE_TTL,
                new TypeReference<List<BehaviorStat>>() {},
                () -> dashboardMapper.userBehaviorStats(userId));
    }

    @GetMapping("/user/{userId}/hourly-trend")
    public List<HourlyTrend> userHourlyTrend(@PathVariable Long userId) {
        return cacheUtil.get("dashboard:user:" + userId + ":hourly-trend", CACHE_TTL,
                new TypeReference<List<HourlyTrend>>() {},
                () -> dashboardMapper.userHourlyTrend(userId));
    }
}
