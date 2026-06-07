package org.example.videorecommend;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
@MapperScan("org.example.videorecommend.mapper")
public class VideoRecommendApplication {
    public static void main(String[] args) {
        SpringApplication.run(VideoRecommendApplication.class, args);
    }
}
