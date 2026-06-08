package org.example.videorecommend.entity;
import lombok.Data;
import java.util.List;
import java.util.Map;
@Data
public class RealtimeData {
    private Long totalBehaviors;
    private List<Map<String, Object>> categoryTop5;
    private List<String> recentActions;
}
