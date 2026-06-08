package org.example.videorecommend;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {"server.port=0"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisDashboardCacheTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_BEHAVIOR = "dashboard:behavior_stats_test";
    private static final String KEY_HOURLY = "dashboard:hourly_trend_test";

    @Test
    @Order(1)
    void testWriteAndReadBehaviorStats() {
        // 模拟 behavior-stats 数据: JSON 数组字符串
        String mockJson = "[{\"category\":\"food\",\"avgViewTime\":1520.5,\"likeRate\":0.35,\"relayRate\":0.12,\"behaviorCount\":4200},{\"category\":\"tourism\",\"avgViewTime\":2100.0,\"likeRate\":0.28,\"relayRate\":0.08,\"behaviorCount\":3800}]";

        // 写入 Redis
        redisTemplate.opsForValue().set(KEY_BEHAVIOR, mockJson, 1, TimeUnit.HOURS);

        // 读取并验证
        String saved = redisTemplate.opsForValue().get(KEY_BEHAVIOR);
        assertNotNull(saved, "Redis 中应存在 behavior_stats");
        assertTrue(saved.contains("food"), "应包含 food 分类");
        assertTrue(saved.contains("tourism"), "应包含 tourism 分类");
        assertTrue(saved.contains("avgViewTime"), "应包含 avgViewTime 字段");
        assertTrue(saved.contains("behaviorCount"), "应包含 behaviorCount 字段");
        assertTrue(saved.startsWith("["), "应以 [ 开头");
        assertTrue(saved.endsWith("]"), "应以 ] 结尾");

        System.out.println("behavior-stats 写入/读取验证通过");
    }

    @Test
    @Order(2)
    void testWriteAndReadHourlyTrend() {
        // 构建 24 小时 JSON 数组
        StringBuilder sb = new StringBuilder("[");
        for (int h = 0; h < 24; h++) {
            if (h > 0) sb.append(",");
            sb.append(String.format("{\"hour\":%d,\"count\":%d}", h, 1000 + h * 200));
        }
        sb.append("]");
        String mockJson = sb.toString();

        // 写入 Redis
        redisTemplate.opsForValue().set(KEY_HOURLY, mockJson, 1, TimeUnit.HOURS);

        // 读取并验证
        String saved = redisTemplate.opsForValue().get(KEY_HOURLY);
        assertNotNull(saved);
        assertTrue(saved.contains("\"hour\":0"));
        assertTrue(saved.contains("\"hour\":23"));
        assertTrue(saved.contains("\"count\""));

        // 验证 24 小时
        int count = 0;
        int idx = 0;
        while ((idx = saved.indexOf("\"hour\"", idx)) != -1) {
            count++;
            idx++;
        }
        assertEquals(24, count, "应有 24 小时数据");

        System.out.println("hourly-trend 写入/读取验证通过");
    }

    @Test
    @Order(3)
    void testLargeDatasetWrite() {
        // 模拟更大量数据: 16 个分类
        StringBuilder sb = new StringBuilder("[");
        String[] cats = {"food", "news", "tourism", "daily life", "fashion", "movies",
                         "sports", "technology", "finance", "pets", "health", "musics",
                         "education", "amusement", "cosmetics", "games"};
        for (int i = 0; i < cats.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"category\":\"%s\",\"avgViewTime\":%.1f,\"likeRate\":%.2f,\"relayRate\":%.2f,\"behaviorCount\":%d}",
                    cats[i], 1000 + Math.random() * 3000, 0.1 + Math.random() * 0.6,
                    0.02 + Math.random() * 0.2, 3000 + (int)(Math.random() * 10000)));
        }
        sb.append("]");
        String json = sb.toString();

        // 写入
        redisTemplate.opsForValue().set("dashboard:behavior_stats_large", json, 1, TimeUnit.HOURS);

        // 读取
        String read = redisTemplate.opsForValue().get("dashboard:behavior_stats_large");
        assertNotNull(read);
        assertTrue(read.length() > 200, "JSON 长度应超过 200 字符");

        System.out.println("大数据量写入验证通过, JSON 长度: " + read.length() + " 字符");
    }

    @Test
    @Order(4)
    void testCleanup() {
        redisTemplate.delete(KEY_BEHAVIOR);
        redisTemplate.delete(KEY_HOURLY);
        redisTemplate.delete("dashboard:behavior_stats_large");

        assertNull(redisTemplate.opsForValue().get(KEY_BEHAVIOR));
        assertNull(redisTemplate.opsForValue().get(KEY_HOURLY));
        System.out.println("测试数据清理完成");
    }
}
