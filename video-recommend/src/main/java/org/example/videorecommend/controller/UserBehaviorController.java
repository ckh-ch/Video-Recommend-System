package org.example.videorecommend.controller;

import com.alibaba.fastjson.JSONObject;
import org.example.videorecommend.entity.UserBehavior;
import org.example.videorecommend.mapper.UserBehaviorMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/behavior")
public class UserBehaviorController {

    @Autowired private UserBehaviorMapper behaviorMapper;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;

    @PostMapping
    public String record(@RequestBody UserBehavior behavior) {
        behavior.setBehaviorTime(LocalDateTime.now());
        behaviorMapper.insert(behavior);

        if (kafkaTemplate != null) {
            try {
                JSONObject msg = new JSONObject();
                msg.put("userId", behavior.getUserId());
                msg.put("videoId", behavior.getVideoId());
                msg.put("videoCategory", behavior.getVideoCategory());
                msg.put("likeType", behavior.getLikeType());
                msg.put("relayType", behavior.getRelayType());
                msg.put("viewingTime", behavior.getViewingTime());
                msg.put("timestamp", LocalDateTime.now().toString());
                kafkaTemplate.send("user_behavior", msg.toJSONString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("KafkaTemplate 为 null，无法发送消息到 Kafka");
        }

        return "ok";
    }

    @GetMapping("/user/{userId}")
    public List<UserBehavior> getByUser(@PathVariable Long userId) {
        return behaviorMapper.selectByUserId(userId);
    }
}
