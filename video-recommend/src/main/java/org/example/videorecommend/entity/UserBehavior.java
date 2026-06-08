package org.example.videorecommend.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class UserBehavior {
    private Long id;
    private Long userId;
    private Long videoId;
    private String videoCategory;
    private Integer likeType;
    private Integer relayType;
    private Double viewingTime;
    private LocalDateTime behaviorTime;
}
