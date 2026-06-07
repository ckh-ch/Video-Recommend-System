package org.example.videorecommend.entity;
import lombok.Data;
@Data
public class Video {
    private Long id;
    private String category;
    private String tags;
    private Integer duration;
    private Integer viewCount;
}
