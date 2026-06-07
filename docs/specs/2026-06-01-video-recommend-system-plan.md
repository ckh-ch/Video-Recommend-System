# 短视频推荐系统 — 实现计划

> **For agentic workers:** 使用本计划按阶段逐步实现。每个任务完成后验证再继续。

**目标:** 基于 dy_action_view.csv 数据集，构建完整的短视频推荐系统（冷启动 ALS 训练 + 后端 API + 实时流处理 + 前端展示）

**架构:** 本地开发机运行 Spring Boot 后端 + Vue 前端 + Spark 本地模式，连接远程 VM(192.168.126.130) 的 HDFS/MySQL/Redis/Kafka

**技术栈:** Spring Boot 4.0.6 + Vue 3 + Spark 3.5.4(Scala 2.12) + MySQL + Redis + Kafka + HDFS

---

## 文件结构概览

```
big-data/
├── pom.xml                                                (修改: 添加 Scala 源码目录)
├── src/main/scala/com/video/
│   ├── ColdStartApp.scala        ← 冷启动: ETL + ALS + 写MySQL/Redis
│   └── streaming/
│       └── StreamingApp.scala    ← (阶段三) Kafka 流处理
├── src/main/java/org/example/App.java  (保留不动)
└── data/dy_action_view.csv       ← 原始数据

video-recommend/
├── pom.xml                       (修改: 修正 web 依赖, 添加 kafka/redis/es 依赖)
├── src/main/java/org/example/videorecommend/
│   ├── VideoRecommendApplication.java  (修改: 添加 @MapperScan)
│   ├── entity/
│   │   ├── User.java
│   │   ├── Video.java
│   │   ├── UserBehavior.java
│   │   └── UserProfile.java
│   ├── mapper/
│   │   ├── UserMapper.java
│   │   ├── VideoMapper.java
│   │   ├── UserBehaviorMapper.java
│   │   └── UserProfileMapper.java
│   ├── service/
│   │   ├── RecommendService.java
│   │   └── UserProfileService.java
│   ├── service/impl/
│   │   ├── RecommendServiceImpl.java
│   │   └── UserProfileServiceImpl.java
│   ├── controller/
│   │   ├── RecommendController.java
│   │   ├── UserBehaviorController.java
│   │   ├── UserController.java
│   │   └── VideoController.java
│   └── config/
│       ├── RedisConfig.java
│       └── KafkaConfig.java
└── src/main/resources/
    ├── application.properties
    ├── schema.sql
    └── mapper/
        ├── UserMapper.xml
        ├── VideoMapper.xml
        ├── UserBehaviorMapper.xml
        └── UserProfileMapper.xml

video-front/
├── package.json                  (修改: 添加 vue-router, axios)
├── src/
│   ├── main.js                   (修改: 挂载 router)
│   ├── App.vue                   (修改: 导航栏 + 路由视图)
│   ├── api/
│   │   └── index.js              ← axios 封装
│   ├── router/
│   │   └── index.js              ← 路由配置
│   └── views/
│       ├── RecommendPage.vue     ← 推荐页
│       ├── HotPage.vue           ← 热门页
│       ├── VideoDetail.vue       ← 视频详情
│       └── UserProfile.vue       ← 用户画像
```

---

## 阶段一：冷启动链路

### 任务 1.1：修改 big-data pom.xml 支持 Scala 编译

**文件:** `big-data/pom.xml`

将 `<sourceDirectory>src/main/java</sourceDirectory>` 修改为同时包含 Scala 源码目录，并更新 mainClass：

```xml
<!-- 删除原有 <sourceDirectory> 行，在 <plugins> 前增加： -->
<sourceDirectory>src/main/scala</sourceDirectory>

<!-- 在 maven-assembly-plugin 中修改 mainClass -->
<mainClass>com.video.ColdStartApp</mainClass>
```

同时添加 Spark Streaming 和 Kafka 依赖（阶段三会用，先加上）：

```xml
<!-- 在 <dependencies> 末尾添加 -->
<dependency>
    <groupId>org.apache.spark</groupId>
    <artifactId>spark-streaming_2.12</artifactId>
    <version>${spark.version}</version>
</dependency>
<dependency>
    <groupId>org.apache.spark</groupId>
    <artifactId>spark-streaming-kafka-0-10_2.12</artifactId>
    <version>${spark.version}</version>
</dependency>
```

验证：`mvn compile` 成功。

### 任务 1.2：上传 CSV 到 HDFS

在 VM 上执行：

```bash
# 创建 HDFS 目录
hdfs dfs -mkdir -p /user/video-recommend/raw/

# 上传 CSV（在本地执行 scp，或在 VM 上直接执行）
# 方式1: 本地 scp 到 VM 再 put
scp big-data/data/dy_action_view.csv root@192.168.126.130:/root/
ssh root@192.168.126.130 "hdfs dfs -put /root/dy_action_view.csv /user/video-recommend/raw/"

# 方式2: 本地直接 hdfs put 
hdfs dfs -put big-data/data/dy_action_view.csv hdfs://192.168.126.130:9000/user/video-recommend/raw/

# 验证
hdfs dfs -ls /user/video-recommend/raw/
```

### 任务 1.3：创建 ColdStartApp.scala

**文件:** `big-data/src/main/scala/com/video/ColdStartApp.scala`

```scala
package com.video

import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.ml.recommendation.ALS
import org.apache.spark.sql.functions._
import java.sql.{Connection, DriverManager, PreparedStatement}
import redis.clients.jedis.Jedis
import com.alibaba.fastjson.JSONObject

object ColdStartApp {

  case class Rating(userId: Int, videoId: Int, rating: Float)

  val HDFS_PATH = "hdfs://192.168.126.130:9000/user/video-recommend/raw/dy_action_view.csv"
  val MYSQL_URL = "jdbc:mysql://192.168.126.130:3306/video_recommend?useSSL=false&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
  val MYSQL_USER = "root"
  val MYSQL_PWD = "123456"
  val REDIS_HOST = "192.168.126.130"
  val REDIS_PORT = 6379

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("VideoRecommendColdStart")
      .master("local[*]")
      .config("spark.sql.shuffle.partitions", "10")
      .getOrCreate()

    println("=== 阶段1: 读取并清洗数据 ===")
    val raw = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(HDFS_PATH)
      .na.drop(Seq("user_id", "video_id", "viewing_time"))
      .filter("viewing_time > 0")
      .dropDuplicates("user_id", "video_id", "time")

    raw.createOrReplaceTempView("behavior")
    println(s"有效行为数: ${raw.count()}")

    println("=== 提取唯一用户和视频 ===")
    val usersDF = raw.select("user_id").distinct()
      .withColumnRenamed("user_id", "id")
      .withColumn("username", concat(lit("user_"), col("id")))
    val videosDF = raw.select("video_id", "video_category").distinct()
      .withColumnRenamed("video_id", "id")
      .withColumnRenamed("video_category", "category")
      .withColumn("tags", col("category"))
      .withColumn("view_count", lit(0))

    println("=== 写入用户和视频到 MySQL ===")
    usersDF.write.mode(SaveMode.Overwrite)
      .option("batchsize", "1000")
      .jdbc(MYSQL_URL, "users", new java.util.Properties() {
        put("user", MYSQL_USER); put("password", MYSQL_PWD)
      })
    videosDF.write.mode(SaveMode.Overwrite)
      .option("batchsize", "1000")
      .jdbc(MYSQL_URL, "videos", new java.util.Properties() {
        put("user", MYSQL_USER); put("password", MYSQL_PWD)
      })

    println("=== 阶段2: ALS 协同过滤训练 ===")
    val userIndexer = raw.select("user_id").distinct().rdd
      .zipWithIndex().map { case (row, idx) => (row.getLong(0), idx.toInt) }
      .toDF("raw_user_id", "user_idx")
    val videoIndexer = raw.select("video_id").distinct().rdd
      .zipWithIndex().map { case (row, idx) => (row.getLong(0), idx.toInt) }
      .toDF("raw_video_id", "video_idx")

    val ratingDF = raw.join(userIndexer, col("user_id") === col("raw_user_id"))
      .join(videoIndexer, col("video_id") === col("raw_video_id"))
      .select(
        col("user_idx").as("userId"),
        col("video_idx").as("videoId"),
        (col("like_type") * 2.0 + col("relay_type") * 1.0 +
          (col("viewing_time") / 3000.0) * 0.5).as("rating")
      )
      .filter("rating > 0")

    val als = new ALS()
      .setMaxIter(10)
      .setRank(10)
      .setRegParam(0.1)
      .setUserCol("userId")
      .setItemCol("videoId")
      .setRatingCol("rating")

    val model = als.fit(ratingDF)

    println("=== 为每个用户生成 Top 50 推荐 ===")
    // recommendForAllUsers 返回 (userId: Int, recommendations: Array[Struct(videoId, rating)])
    import spark.implicits._
    val userRecs = model.recommendForAllUsers(50)
      .withColumn("rec_exploded", explode(col("recommendations")))
      .withColumn("rec_videoId", col("rec_exploded.videoId"))
      .groupBy("userId")
      .agg(collect_list("rec_videoId").as("videoIds"))

    // 将 user_idx 映射回原始 user_id
    val userIdxMapping = userIndexer
      .withColumnRenamed("raw_user_id", "orig_user_id")
      .withColumnRenamed("user_idx", "orig_user_idx")
    val userRecsWithRaw = userRecs
      .join(userIdxMapping, col("userId") === col("orig_user_idx"))
      .select("orig_user_id", "videoIds")

    // 展开推荐结果：每个用户一行，videoIds 逗号分隔
    val results = userRecsWithRaw.rdd.map { row =>
      val uid = row.getLong(0)
      val ids = row.getSeq[Int](1).mkString(",")
      (uid, ids)
    }.toDF("user_id", "video_ids")

    println("=== 阶段3: 用户画像计算 ===")
    val profileDF = raw.groupBy("user_id")
      .agg(
        avg("viewing_time").as("avg_viewing_time"),
        count("*").as("total_watch_count"),
        (sum("like_type").cast("double") / count("*")).as("like_rate"),
        when(count("*") >= 100, 5).when(count("*") >= 50, 4)
          .when(count("*") >= 20, 3).when(count("*") >= 5, 2)
          .otherwise(1).as("active_level")
      )

    println("=== 阶段4: 写入 MySQL 和 Redis ===")
    // 写入 MySQL
    results.write.mode(SaveMode.Overwrite)
      .option("batchsize", "1000")
      .jdbc(MYSQL_URL, "recommend_results", new java.util.Properties() {
        put("user", MYSQL_USER); put("password", MYSQL_PWD)
      })
    profileDF.write.mode(SaveMode.Overwrite)
      .jdbc(MYSQL_URL, "user_profile", new java.util.Properties() {
        put("user", MYSQL_USER); put("password", MYSQL_PWD)
      })

    // 写入 Redis
    val jedis = new Jedis(REDIS_HOST, REDIS_PORT)
    results.collect().foreach { row =>
      val uid = row.getLong(0)
      val vids = row.getString(1)
      jedis.setex(s"rec:$uid", 86400, vids)
    }
    profileDF.collect().foreach { row =>
      val uid = row.getLong(0)
      val json = new JSONObject()
      json.put("avgViewTime", row.getDouble(1))
      json.put("totalWatch", row.getLong(2))
      json.put("likeRate", row.getDouble(3))
      json.put("activeLevel", row.getLong(4))
      jedis.setex(s"profile:$uid:stats", 86400, json.toJSONString)
    }
    jedis.close()

    println("=== 冷启动完成! ===")
    spark.stop()
  }
}
```

### 任务 1.4：初始化 MySQL 表结构

**文件:** `video-recommend/src/main/resources/schema.sql`

```sql
CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY,
  username VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS videos (
  id BIGINT PRIMARY KEY,
  category VARCHAR(50),
  tags VARCHAR(500),
  duration INT DEFAULT 0,
  view_count INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_behavior (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  video_category VARCHAR(50),
  like_type INT DEFAULT 0,
  relay_type INT DEFAULT 0,
  viewing_time DOUBLE DEFAULT 0,
  behavior_time DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_behavior_time (behavior_time)
);

CREATE TABLE IF NOT EXISTS user_profile (
  user_id BIGINT PRIMARY KEY,
  interest_tags VARCHAR(500),
  avg_viewing_time DOUBLE DEFAULT 0,
  total_watch_count INT DEFAULT 0,
  like_rate DOUBLE DEFAULT 0,
  active_level INT DEFAULT 1,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recommend_results (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  video_ids TEXT NOT NULL,
  strategy VARCHAR(20) DEFAULT 'ALS',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_id (user_id)
);
```

**在 VM 上执行建表：**

```bash
mysql -h 192.168.126.130 -u root -p -e "CREATE DATABASE IF NOT EXISTS video_recommend DEFAULT CHARSET utf8mb4;"
mysql -h 192.168.126.130 -u root -p video_recommend < schema.sql
```

或者用 root 密码 123456 直接连接执行。

### 任务 1.5：运行 ColdStartApp 验证

```bash
# 本地执行（big-data 目录下）
cd big-data
mvn clean package -DskipTests
java -cp target/big-data-1.0-SNAPSHOT-jar-with-dependencies.jar com.video.ColdStartApp

# 验证 MySQL 中有数据
mysql -h 192.168.126.130 -u root -p video_recommend -e "SELECT COUNT(*) FROM users; SELECT COUNT(*) FROM videos; SELECT COUNT(*) FROM recommend_results;"

# 验证 Redis 中有数据
redis-cli -h 192.168.126.130 keys "rec:*" | head -5
redis-cli -h 192.168.126.130 keys "profile:*" | head -5
```

---

## 阶段二：后端 API

### 任务 2.1：修正 pom.xml 并添加依赖

**文件:** `video-recommend/pom.xml`

关键修改：
- `spring-boot-starter-webmvc` → `spring-boot-starter-web`
- 添加 `spring-kafka` 依赖
- 测试依赖修正

```xml
<!-- 替换 webmvc 依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- 在 <dependencies> 末尾添加 -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### 任务 2.2：创建 application.properties

**文件:** `video-recommend/src/main/resources/application.properties`

```properties
spring.application.name=video-recommend
server.port=8080

# MySQL
spring.datasource.url=jdbc:mysql://192.168.126.130:3306/video_recommend?useSSL=false&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=123456
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.sql.init.mode=always
spring.sql.init.schema-locations=classpath:schema.sql

# MyBatis
mybatis.configuration.map-underscore-to-camel-case=true
mybatis.mapper-locations=classpath:mapper/*.xml

# Redis
spring.data.redis.host=192.168.126.130
spring.data.redis.port=6379
spring.data.redis.password=

# Kafka
spring.kafka.bootstrap-servers=192.168.126.130:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer
# 由于 Spring Boot 4.x 的改动，Kafka 自动配置可能变化，采用手动配置方式
```

### 任务 2.3：创建实体类

**文件:** `video-recommend/src/main/java/org/example/videorecommend/entity/User.java`

```java
package org.example.videorecommend.entity;
import lombok.Data;
@Data
public class User {
    private Long id;
    private String username;
}
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/entity/Video.java`

```java
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
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/entity/UserBehavior.java`

```java
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
    private LocalDateTime createdAt;
}
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/entity/UserProfile.java`

```java
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
```

### 任务 2.4：创建 Mapper 接口

**文件:** `video-recommend/src/main/java/org/example/videorecommend/mapper/UserMapper.java`

```java
package org.example.videorecommend.mapper;
import org.example.videorecommend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
@Mapper
public interface UserMapper {
    @Select("SELECT * FROM users WHERE id = #{id}")
    User selectById(Long id);
    @Select("SELECT * FROM users")
    List<User> selectAll();
}
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/mapper/VideoMapper.java`

```java
package org.example.videorecommend.mapper;
import org.example.videorecommend.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
@Mapper
public interface VideoMapper {
    @Select("SELECT * FROM videos WHERE id = #{id}")
    Video selectById(Long id);
    @Select("SELECT * FROM videos")
    List<Video> selectAll();
    @Select("SELECT * FROM videos WHERE category = #{category} LIMIT #{limit}")
    List<Video> selectByCategory(String category, int limit);
    @Select("SELECT * FROM videos ORDER BY view_count DESC LIMIT #{limit}")
    List<Video> selectHotVideos(int limit);
}
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/mapper/UserBehaviorMapper.java`

```java
package org.example.videorecommend.mapper;
import org.example.videorecommend.entity.UserBehavior;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
@Mapper
public interface UserBehaviorMapper {
    @Select("SELECT * FROM user_behavior WHERE id = #{id}")
    UserBehavior selectById(Long id);
    @Select("SELECT * FROM user_behavior WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT 100")
    List<UserBehavior> selectByUserId(Long userId);
    int insert(UserBehavior behavior);
    @Select("SELECT video_category, COUNT(*) as cnt FROM user_behavior WHERE user_id = #{userId} GROUP BY video_category ORDER BY cnt DESC LIMIT 5")
    List<java.util.Map<String, Object>> selectUserCategoryPref(Long userId);
}
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/mapper/UserProfileMapper.java`

```java
package org.example.videorecommend.mapper;
import org.example.videorecommend.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface UserProfileMapper {
    @Select("SELECT * FROM user_profile WHERE user_id = #{userId}")
    UserProfile selectByUserId(Long userId);
    int upsert(UserProfile profile);
}
```

### 任务 2.5：创建 MyBatis XML 映射

**文件:** `video-recommend/src/main/resources/mapper/UserBehaviorMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="org.example.videorecommend.mapper.UserBehaviorMapper">
    <insert id="insert" parameterType="org.example.videorecommend.entity.UserBehavior" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO user_behavior (user_id, video_id, video_category, like_type, relay_type, viewing_time, behavior_time)
        VALUES (#{userId}, #{videoId}, #{videoCategory}, #{likeType}, #{relayType}, #{viewingTime}, #{behaviorTime})
    </insert>
</mapper>
```

**文件:** `video-recommend/src/main/resources/mapper/UserProfileMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="org.example.videorecommend.mapper.UserProfileMapper">
    <insert id="upsert" parameterType="org.example.videorecommend.entity.UserProfile">
        INSERT INTO user_profile (user_id, interest_tags, avg_viewing_time, total_watch_count, like_rate, active_level)
        VALUES (#{userId}, #{interestTags}, #{avgViewingTime}, #{totalWatchCount}, #{likeRate}, #{activeLevel})
        ON DUPLICATE KEY UPDATE
        interest_tags = VALUES(interest_tags),
        avg_viewing_time = VALUES(avg_viewing_time),
        total_watch_count = VALUES(total_watch_count),
        like_rate = VALUES(like_rate),
        active_level = VALUES(active_level)
    </insert>
</mapper>
```

### 任务 2.6：创建 Service 层

**文件:** `video-recommend/src/main/java/org/example/videorecommend/service/RecommendService.java`

```java
package org.example.videorecommend.service;
import org.example.videorecommend.entity.Video;
import java.util.List;
public interface RecommendService {
    List<Video> getPersonalizedRecommend(Long userId, int limit);
    List<Video> getHotRecommend(int limit);
    List<Video> getCategoryRecommend(String category, int limit);
}
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/service/impl/RecommendServiceImpl.java`

```java
package org.example.videorecommend.service.impl;
import org.example.videorecommend.entity.Video;
import org.example.videorecommend.mapper.VideoMapper;
import org.example.videorecommend.service.RecommendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class RecommendServiceImpl implements RecommendService {
    @Autowired private VideoMapper videoMapper;
    @Autowired private StringRedisTemplate redisTemplate;

    @Override
    public List<Video> getPersonalizedRecommend(Long userId, int limit) {
        String redisKey = "rec:" + userId;
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null && !cached.isEmpty()) {
            List<Long> ids = Arrays.stream(cached.split(","))
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
            if (!ids.isEmpty()) {
                List<Long> limitedIds = ids.stream().limit(limit).collect(Collectors.toList());
                return videoMapper.selectByIds(limitedIds);
            }
        }
        return getHotRecommend(limit);
    }

    @Override
    public List<Video> getHotRecommend(int limit) {
        String cached = redisTemplate.opsForValue().get("hot_videos");
        if (cached != null) return Collections.emptyList();
        List<Video> hot = videoMapper.selectHotVideos(limit);
        return hot;
    }

    @Override
    public List<Video> getCategoryRecommend(String category, int limit) {
        return videoMapper.selectByCategory(category, limit);
    }
}
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/service/UserProfileService.java`

```java
package org.example.videorecommend.service;
import org.example.videorecommend.entity.UserProfile;
public interface UserProfileService {
    UserProfile getUserProfile(Long userId);
}
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/service/impl/UserProfileServiceImpl.java`

```java
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
```

### 任务 2.7：创建 Controller

**文件:** `video-recommend/src/main/java/org/example/videorecommend/controller/RecommendController.java`

```java
package org.example.videorecommend.controller;
import org.example.videorecommend.entity.Video;
import org.example.videorecommend.service.RecommendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/recommend")
public class RecommendController {
    @Autowired private RecommendService recommendService;

    @GetMapping("/personalized/{userId}")
    public List<Video> personalized(@PathVariable Long userId, @RequestParam(defaultValue = "20") int limit) {
        return recommendService.getPersonalizedRecommend(userId, limit);
    }

    @GetMapping("/hot")
    public List<Video> hot(@RequestParam(defaultValue = "20") int limit) {
        return recommendService.getHotRecommend(limit);
    }

    @GetMapping("/category/{category}")
    public List<Video> byCategory(@PathVariable String category, @RequestParam(defaultValue = "20") int limit) {
        return recommendService.getCategoryRecommend(category, limit);
    }
}
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/controller/UserBehaviorController.java`

```java
package org.example.videorecommend.controller;
import org.example.videorecommend.entity.UserBehavior;
import org.example.videorecommend.mapper.UserBehaviorMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson.JSONObject;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/behavior")
public class UserBehaviorController {
    @Autowired private UserBehaviorMapper behaviorMapper;
    @Autowired(required = false) private KafkaTemplate<String, String> kafkaTemplate;

    @PostMapping
    public String record(@RequestBody UserBehavior behavior) {
        behavior.setBehaviorTime(LocalDateTime.now());
        behaviorMapper.insert(behavior);

        if (kafkaTemplate != null) {
            JSONObject msg = new JSONObject();
            msg.put("userId", behavior.getUserId());
            msg.put("videoId", behavior.getVideoId());
            msg.put("videoCategory", behavior.getVideoCategory());
            msg.put("likeType", behavior.getLikeType());
            msg.put("relayType", behavior.getRelayType());
            msg.put("viewingTime", behavior.getViewingTime());
            msg.put("timestamp", LocalDateTime.now().toString());
            kafkaTemplate.send("user_behavior", msg.toJSONString());
        }
        return "ok";
    }

    @GetMapping("/user/{userId}")
    public List<UserBehavior> getByUser(@PathVariable Long userId) {
        return behaviorMapper.selectByUserId(userId);
    }
}
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/controller/UserController.java`

```java
package org.example.videorecommend.controller;
import org.example.videorecommend.entity.User;
import org.example.videorecommend.entity.UserProfile;
import org.example.videorecommend.mapper.UserMapper;
import org.example.videorecommend.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired private UserMapper userMapper;
    @Autowired private UserProfileService userProfileService;

    @GetMapping
    public List<User> all() { return userMapper.selectAll(); }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) { return userMapper.selectById(id); }

    @GetMapping("/{id}/profile")
    public UserProfile profile(@PathVariable Long id) { return userProfileService.getUserProfile(id); }
}
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/controller/VideoController.java`

```java
package org.example.videorecommend.controller;
import org.example.videorecommend.entity.Video;
import org.example.videorecommend.mapper.VideoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/videos")
public class VideoController {
    @Autowired private VideoMapper videoMapper;

    @GetMapping
    public List<Video> all() { return videoMapper.selectAll(); }

    @GetMapping("/{id}")
    public Video get(@PathVariable Long id) { return videoMapper.selectById(id); }

    @GetMapping("/hot")
    public List<Video> hot(@RequestParam(defaultValue = "10") int limit) {
        return videoMapper.selectHotVideos(limit);
    }

    @GetMapping("/category/{category}")
    public List<Video> byCategory(@PathVariable String category, @RequestParam(defaultValue = "10") int limit) {
        return videoMapper.selectByCategory(category, limit);
    }
}
```

### 任务 2.8：添加 selectByIds 方法到 VideoMapper

由于 `selectByIds` 需要拼接 SQL，在 `VideoMapper.java` 中添加：

```java
@Select("<script>"
    + "SELECT * FROM videos WHERE id IN "
    + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>"
    + "</script>")
List<Video> selectByIds(@Param("ids") List<Long> ids);
```

同时修改 `RecommendServiceImpl.java` 中对应的调用（已改为 List<Long> 参数），并添加 `import org.apache.ibatis.annotations.Param;` 到 VideoMapper。

### 任务 2.9：更新启动类并配置 Redis/Kafka

**文件:** `video-recommend/src/main/java/org/example/videorecommend/VideoRecommendApplication.java`

```java
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
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/config/RedisConfig.java`

```java
package org.example.videorecommend.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
```

**文件:** `video-recommend/src/main/java/org/example/videorecommend/config/KafkaConfig.java`

```java
package org.example.videorecommend.config;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.126.130:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

### 任务 2.10：验证后端 API

```bash
cd video-recommend
mvn spring-boot:run
```

验证接口：
```bash
curl http://localhost:8080/api/recommend/hot?limit=5
curl http://localhost:8080/api/recommend/personalized/85500?limit=5
curl http://localhost:8080/api/users/85500/profile
curl -X POST http://localhost:8080/api/behavior \
  -H "Content-Type: application/json" \
  -d '{"userId":85500,"videoId":834124,"videoCategory":"food","likeType":1,"viewingTime":720}'
```

---

## 阶段三：实时流处理

### 任务 3.1：创建 StreamingApp

**文件:** `big-data/src/main/scala/com/video/streaming/StreamingApp.scala`

```scala
package com.video.streaming

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import java.sql.{Connection, DriverManager, PreparedStatement}
import redis.clients.jedis.Jedis
import com.alibaba.fastjson.JSON

object StreamingApp {
  val MYSQL_URL = "jdbc:mysql://192.168.126.130:3306/video_recommend?useSSL=false&serverTimezone=Asia/Shanghai"
  val MYSQL_USER = "root"
  val MYSQL_PWD = "123456"
  val REDIS_HOST = "192.168.126.130"
  val REDIS_PORT = 6379

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("VideoRecommendStreaming")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "192.168.126.130:9092")
      .option("subscribe", "user_behavior")
      .option("startingOffsets", "latest")
      .load()
      .selectExpr("CAST(value AS STRING) as json")
      .select(
        get_json_object($"json", "$.userId").cast("long").as("user_id"),
        get_json_object($"json", "$.videoCategory").as("video_category"),
        get_json_object($"json", "$.likeType").cast("int").as("like_type"),
        get_json_object($"json", "$.viewingTime").cast("double").as("viewing_time")
      )

    val query = df.writeStream
      .trigger(Trigger.ProcessingTime("10 seconds"))
      .foreachBatch { (batchDF, batchId) =>
        if (!batchDF.isEmpty) {
          batchDF.collect().foreach { row =>
            val uid = row.getLong(0)
            val category = row.getString(1)
            val likeType = row.getInt(2)
            val viewTime = row.getDouble(3)

            val jedis = new Jedis(REDIS_HOST, REDIS_PORT)
            try {
              jedis.hincrBy(s"profile:$uid:cats", if (category != null) category else "unknown", 1)
              jedis.hincrBy(s"profile:$uid:stats", "totalWatch", 1)
              if (likeType == 1) jedis.hincrBy(s"profile:$uid:stats", "totalLike", 1)
              jedis.hincrByFloat(s"profile:$uid:stats", "totalViewTime", viewTime)
            } finally {
              jedis.close()
            }
          }
        }
      }
      .start()

    query.awaitTermination()
  }
}
```

### 任务 3.2：添加 Spark Streaming Kafka 依赖

**文件:** `big-data/pom.xml`（已在任务 1.1 中添加）

验证：`mvn package -DskipTests`

### 任务 3.3：端到端联调

```bash
# 终端1: 启动后端 API
cd video-recommend && mvn spring-boot:run

# 终端2: 启动 StreamingApp
cd big-data
java -cp target/big-data-1.0-SNAPSHOT-jar-with-dependencies.jar com.video.streaming.StreamingApp

# 终端3: 模拟用户行为
curl -X POST http://localhost:8080/api/behavior \
  -H "Content-Type: application/json" \
  -d '{"userId":85500,"videoId":834124,"videoCategory":"food","likeType":1,"viewingTime":720.0}'

# 验证 Redis 实时更新
redis-cli -h 192.168.126.130 hgetall "profile:85500:stats"
```

---

## 阶段四：前端展示

### 任务 4.1：安装前端依赖

```bash
cd video-front
npm install vue-router@4 axios
```

### 任务 4.2：创建 API 封装

**文件:** `video-front/src/api/index.js`

```javascript
import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000
})

export function getPersonalizedRecommend(userId, limit = 20) {
  return api.get(`/recommend/personalized/${userId}`, { params: { limit } })
}

export function getHotRecommend(limit = 20) {
  return api.get('/recommend/hot', { params: { limit } })
}

export function getCategoryRecommend(category, limit = 20) {
  return api.get(`/recommend/category/${category}`, { params: { limit } })
}

export function getVideoDetail(id) {
  return api.get(`/videos/${id}`)
}

export function getUserProfile(userId) {
  return api.get(`/users/${userId}/profile`)
}

export function recordBehavior(data) {
  return api.post('/behavior', data)
}

export default api
```

### 任务 4.3：创建路由配置

**文件:** `video-front/src/router/index.js`

```javascript
import { createRouter, createWebHistory } from 'vue-router'
import RecommendPage from '../views/RecommendPage.vue'
import HotPage from '../views/HotPage.vue'
import VideoDetail from '../views/VideoDetail.vue'
import UserProfile from '../views/UserProfile.vue'

const routes = [
  { path: '/', redirect: '/recommend/85500' },
  { path: '/recommend/:userId', component: RecommendPage },
  { path: '/hot', component: HotPage },
  { path: '/video/:id', component: VideoDetail },
  { path: '/profile/:userId', component: UserProfile }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
```

### 任务 4.4：创建推荐页面

**文件:** `video-front/src/views/RecommendPage.vue`

```vue
<template>
  <div class="recommend-page">
    <h2>个性化推荐</h2>
    <div class="user-select">
      <label>用户ID：</label>
      <input v-model.number="userId" type="number" @change="loadRecommend" />
      <button @click="loadRecommend">刷新</button>
    </div>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else class="video-grid">
      <div v-for="video in videos" :key="video.id" class="video-card" @click="$router.push(`/video/${video.id}`)">
        <div class="video-category">{{ video.category }}</div>
        <div class="video-tags">{{ video.tags }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getPersonalizedRecommend } from '../api/index.js'

const route = useRoute()
const userId = ref(parseInt(route.params.userId) || 85500)
const videos = ref([])
const loading = ref(false)

async function loadRecommend() {
  loading.value = true
  try {
    const res = await getPersonalizedRecommend(userId.value)
    videos.value = res.data
  } catch (e) {
    console.error(e)
    videos.value = []
  }
  loading.value = false
}

onMounted(loadRecommend)
</script>

<style scoped>
.recommend-page { padding: 20px; max-width: 1200px; margin: 0 auto; }
.video-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; }
.video-card { background: #f5f5f5; border-radius: 8px; padding: 16px; cursor: pointer; transition: transform .2s; }
.video-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,.15); }
.video-category { font-size: 14px; color: #fb7299; font-weight: bold; margin-bottom: 4px; }
.video-tags { font-size: 12px; color: #666; }
.user-select { margin-bottom: 20px; }
.user-select input { width: 100px; margin: 0 8px; padding: 4px 8px; }
.loading { text-align: center; color: #999; font-size: 16px; padding: 40px; }
</style>
```

### 任务 4.5：创建其他视图页面

**文件:** `video-front/src/views/HotPage.vue` — 热门视频列表（类似 RecommendPage，调用 getHotRecommend）

**文件:** `video-front/src/views/VideoDetail.vue` — 视频详情（显示视频信息 + 点赞/观看行为记录按钮）

**文件:** `video-front/src/views/UserProfile.vue` — 用户画像展示（显示用户画像数据）

### 任务 4.6：更新 main.js 和 App.vue

**文件:** `video-front/src/main.js`

```javascript
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'

createApp(App).use(router).mount('#app')
```

**文件:** `video-front/src/App.vue`

```vue
<template>
  <div id="app">
    <nav class="nav-bar">
      <router-link to="/recommend/85500">推荐</router-link>
      <router-link to="/hot">热门</router-link>
      <router-link to="/profile/85500">我的画像</router-link>
    </nav>
    <router-view />
  </div>
</template>

<script setup>
</script>

<style>
.nav-bar { background: #fb7299; padding: 12px 20px; display: flex; gap: 20px; }
.nav-bar a { color: white; text-decoration: none; font-size: 16px; }
.nav-bar a:hover { opacity: .8; }
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, sans-serif; }
</style>
```

### 任务 4.7：验证前端

```bash
cd video-front
npm run dev
```

浏览器打开 http://localhost:5173 验证页面展示。

---

## 验证清单

每个阶段完成后，运行以下验证：

| 阶段 | 验证命令 | 预期结果 |
|------|---------|---------|
| 一 | `redis-cli -h 192.168.126.130 keys "rec:*" \| head -3` | 有推荐数据 |
| 一 | `mysql -h 192.168.126.130 -u root -p video_recommend -e "SELECT COUNT(*) FROM recommend_results"` | 有数据 |
| 二 | `curl http://localhost:8080/api/recommend/hot?limit=3` | 返回 JSON 视频列表 |
| 三 | `redis-cli -h 192.168.126.130 hgetall "profile:85500:stats"` | 有统计数据 |
| 四 | 浏览器打开 http://localhost:5173 | 页面正常展示 |

---

## 注意问题

1. **big-data ColdStartApp 中 ALS rating 计算**：`viewing_time` 归一化使用固定值 3000（基于数据分布估算），如 ALS 效果不佳可调整
2. **selectByIds 方法**：MyBatis 注解方式使用 `<script><foreach>` 需要确保 MyBatis 版本兼容
3. **Kafka 自动创建 Topic**：Kafka 默认 `auto.create.topics.enable=true`，无需手动创建 `user_behavior` topic
4. **MySQL 建表**：Spring Boot 启动时通过 `schema.sql` 自动建表，也可在 VM 上手动执行
