package org.example.videorecommend.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class UserProfile {
    private Long userId;
    private String interestTags;
    private Double avgViewingTime;
    private Integer totalWatchCount;
    private Double likeRate;
    private Integer activeLevel;
    private LocalDateTime updateTime;
}
