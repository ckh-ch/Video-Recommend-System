package org.example.videorecommend.service.impl;
import org.example.videorecommend.entity.Video;
import org.example.videorecommend.mapper.VideoMapper;
import org.example.videorecommend.service.RecommendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
@Service
public class RecommendServiceImpl implements RecommendService {
    @Autowired private VideoMapper videoMapper;
    @Autowired(required = false) private StringRedisTemplate redisTemplate;
    @Override
    public List<Video> getPersonalizedRecommend(Long userId, int limit) {
        if (redisTemplate != null) {
            String cached = redisTemplate.opsForValue().get("rec:" + userId);
            if (cached != null && !cached.isEmpty()) {
                List<Long> ids = Arrays.stream(cached.split(","))
                    .filter(s -> !s.isEmpty()).map(Long::parseLong)
                    .collect(Collectors.toList());
                if (!ids.isEmpty()) {
                    List<Long> candidateIds = ids.stream().limit(limit * 3).collect(Collectors.toList());
                    List<Video> candidates = videoMapper.selectByIds(candidateIds);
                    return diversifyByCategory(candidates, limit);
                }
            }
        }
        return getHotRecommend(limit);
    }

    private List<Video> diversifyByCategory(List<Video> videos, int limit) {
        Map<String, List<Video>> grouped = new LinkedHashMap<>();
        for (Video v : videos) {
            String cat = v.getCategory() != null ? v.getCategory() : "other";
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(v);
        }
        int maxPerCat = Math.max(2, limit / Math.max(grouped.size(), 1));
        for (List<Video> list : grouped.values()) {
            while (list.size() > maxPerCat) {
                list.remove(list.size() - 1);
            }
        }
        List<Video> result = new ArrayList<>();
        while (result.size() < limit) {
            boolean added = false;
            for (List<Video> list : grouped.values()) {
                if (!list.isEmpty() && result.size() < limit) {
                    result.add(list.remove(0));
                    added = true;
                }
            }
            if (!added) break;
        }
        return result;
    }
    @Override
    public List<Video> getHotRecommend(int limit) {
        return videoMapper.selectHotVideos(limit);
    }
    @Override
    public List<Video> getCategoryRecommend(String category, int limit) {
        return videoMapper.selectByCategory(category, limit);
    }
}
