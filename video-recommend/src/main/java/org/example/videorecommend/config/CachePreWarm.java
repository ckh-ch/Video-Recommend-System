package org.example.videorecommend.config;

import org.example.videorecommend.mapper.DashboardMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CachePreWarm implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CachePreWarm.class);

    @Autowired private RedisCacheUtil cacheUtil;
    @Autowired private DashboardMapper dashboardMapper;

    @Override
    public void run(ApplicationArguments args) {
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                log.info("预热: behavior-stats...");
                long t1 = System.currentTimeMillis();
                cacheUtil.get("dashboard:behavior-stats", 300,
                        new tools.jackson.core.type.TypeReference<java.util.List<?>>() {},
                        () -> dashboardMapper.behaviorStats());
                log.info("预热: behavior-stats 完成 ({}ms)", System.currentTimeMillis() - t1);

                long t2 = System.currentTimeMillis();
                cacheUtil.get("dashboard:hourly-trend", 300,
                        new tools.jackson.core.type.TypeReference<java.util.List<?>>() {},
                        () -> dashboardMapper.hourlyTrend());
                log.info("预热: hourly-trend 完成 ({}ms)", System.currentTimeMillis() - t2);

                log.info("缓存预热全部完成");
            } catch (Exception e) {
                log.warn("缓存预热失败: {}", e.getMessage());
            }
        }, "cache-prewarm").start();
    }
}
