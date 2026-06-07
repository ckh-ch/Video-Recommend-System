package com.video

import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.ml.recommendation.ALS
import org.apache.spark.sql.functions._
import redis.clients.jedis.Jedis

object ColdStartApp {

  val HDFS_PATH = "hdfs://192.168.126.130:8020/user/video-recommend/raw/dy_action_view.csv"
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
    import spark.implicits._
    val userIndexer = raw.select("user_id").distinct().rdd
      .zipWithIndex().map { case (row, idx) => (row.get(0).toString.toLong, idx.toInt) }
      .toDF("raw_user_id", "user_idx")
    val videoIndexer = raw.select("video_id").distinct().rdd
      .zipWithIndex().map { case (row, idx) => (row.get(0).toString.toLong, idx.toInt) }
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
    import spark.implicits._
    val userRecs = model.recommendForAllUsers(50)
      .withColumn("rec_exploded", explode(col("recommendations")))
      .withColumn("rec_videoId", col("rec_exploded.videoId"))
      .groupBy("userId")
      .agg(collect_list("rec_videoId").as("videoIds"))

    val userIdxMapping = userIndexer
      .withColumnRenamed("raw_user_id", "orig_user_id")
      .withColumnRenamed("user_idx", "orig_user_idx")

    val videoIdxMapping = videoIndexer
      .withColumnRenamed("raw_video_id", "orig_video_id")
      .withColumnRenamed("video_idx", "orig_video_idx")
    val vidMap = videoIdxMapping.collect().map(r => (r.getInt(1), r.getLong(0))).toMap

    val userRecsWithRaw = userRecs
      .join(userIdxMapping, col("userId") === col("orig_user_idx"))
      .select("orig_user_id", "videoIds")

    val results = userRecsWithRaw.rdd.map { row =>
      val uid = row.get(0).toString.toLong
      val idxList = row.getSeq[Int](1)
      val origIds = idxList.map(idx => vidMap.getOrElse(idx, idx.toLong)).mkString(",")
      (uid, origIds)
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
    results.write.mode(SaveMode.Overwrite)
      .option("batchsize", "1000")
      .jdbc(MYSQL_URL, "recommend_results", new java.util.Properties() {
        put("user", MYSQL_USER); put("password", MYSQL_PWD)
      })
    profileDF.write.mode(SaveMode.Overwrite)
      .jdbc(MYSQL_URL, "user_profile", new java.util.Properties() {
        put("user", MYSQL_USER); put("password", MYSQL_PWD)
      })

    val jedis = new Jedis(REDIS_HOST, REDIS_PORT)
    results.collect().foreach { row =>
      val uid = row.get(0).toString.toLong
      val vids = row.getString(1)
      jedis.setex(s"rec:$uid", 86400L, vids)
    }
    profileDF.collect().foreach { row =>
      val uid = row.get(0).toString.toLong
      val statsKey = s"profile:$uid:stats"
      val jedis = new Jedis(REDIS_HOST, REDIS_PORT)
      try {
        jedis.hset(statsKey, "avgViewTime", row.getDouble(1).toString)
        jedis.hset(statsKey, "totalWatch", row.get(2).toString.toLong.toString)
        jedis.hset(statsKey, "likeRate", row.getDouble(3).toString)
        jedis.hset(statsKey, "activeLevel", row.get(4).toString.toLong.toString)
        jedis.expire(statsKey, 86400L)
      } finally {
        jedis.close()
      }
    }
    jedis.close()

    println("=== 冷启动完成! ===")
    spark.stop()
  }
}
