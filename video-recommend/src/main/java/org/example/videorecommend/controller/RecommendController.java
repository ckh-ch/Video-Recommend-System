package org.example.videorecommend.controller;
import org.example.videorecommend.entity.Video;
import org.example.videorecommend.service.RecommendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/recommend")
public class RecommendController {
    @Autowired private RecommendService recommendService;
    @GetMapping("/personalized/{userId}")
    public List<Video> personalized(@PathVariable Long userId, @RequestParam(defaultValue = "20") int limit) {
        return recommendService.getPersonalizedRecommend(userId, limit);
    }
    @GetMapping("/hot")
    public List<Video> hot(@RequestParam(defaultValue = "20") int limit) {
        return recommendService.getHotRecommend(limit);
    }
    @GetMapping("/category/{category}")
    public List<Video> byCategory(@PathVariable String category, @RequestParam(defaultValue = "20") int limit) {
        return recommendService.getCategoryRecommend(category, limit);
    }
}
