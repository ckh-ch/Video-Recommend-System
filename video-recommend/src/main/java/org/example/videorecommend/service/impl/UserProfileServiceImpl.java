package org.example.videorecommend.service.impl;
import org.example.videorecommend.entity.UserProfile;
import org.example.videorecommend.mapper.UserProfileMapper;
import org.example.videorecommend.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
@Service
public class UserProfileServiceImpl implements UserProfileService {
    @Autowired private UserProfileMapper userProfileMapper;
    @Override
    public UserProfile getUserProfile(Long userId) {
        return userProfileMapper.selectByUserId(userId);
    }
}
