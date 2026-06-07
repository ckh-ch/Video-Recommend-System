package org.example.videorecommend.controller;
import org.example.videorecommend.entity.User;
import org.example.videorecommend.entity.UserProfile;
import org.example.videorecommend.mapper.UserMapper;
import org.example.videorecommend.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired private UserMapper userMapper;
    @Autowired private UserProfileService userProfileService;
    @GetMapping
    public List<User> all() { return userMapper.selectAll(); }
    @GetMapping("/{id}")
    public User get(@PathVariable Long id) { return userMapper.selectById(id); }
    @GetMapping("/{id}/profile")
    public UserProfile profile(@PathVariable Long id) { return userProfileService.getUserProfile(id); }
}
