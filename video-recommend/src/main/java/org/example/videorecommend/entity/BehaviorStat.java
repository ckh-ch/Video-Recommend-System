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
