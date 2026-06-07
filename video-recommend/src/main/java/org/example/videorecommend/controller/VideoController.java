package org.example.videorecommend.controller;
import org.example.videorecommend.entity.Video;
import org.example.videorecommend.mapper.VideoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/videos")
public class VideoController {
    @Autowired private VideoMapper videoMapper;
    @GetMapping
    public List<Video> all() { return videoMapper.selectAll(); }
    @GetMapping("/{id}")
    public Video get(@PathVariable Long id) { return videoMapper.selectById(id); }
    @GetMapping("/hot")
    public List<Video> hot(@RequestParam(defaultValue = "10") int limit) { return videoMapper.selectHotVideos(limit); }
    @GetMapping("/category/{category}")
    public List<Video> byCategory(@PathVariable String category, @RequestParam(defaultValue = "10") int limit) { return videoMapper.selectByCategory(category, limit); }
}
