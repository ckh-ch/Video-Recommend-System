package org.example.videorecommend.service;
import org.example.videorecommend.entity.Video;
import java.util.List;
public interface RecommendService {
    List<Video> getPersonalizedRecommend(Long userId, int limit);
    List<Video> getHotRecommend(int limit);
    List<Video> getCategoryRecommend(String category, int limit);
}
