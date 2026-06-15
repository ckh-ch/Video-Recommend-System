package com.video.streaming

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import redis.clients.jedis.Jedis

object StreamingApp {
  val REDIS_HOST = "192.168.126.130"
  val REDIS_PORT = 6379

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("VideoRecommendStreaming")
      .master("local[*]")
      .getOrCreate()

    println("=== StreamingApp 启动 ===")
    println(s"Kafka: 192.168.126.130:9092, topic: user_behavior")
    println(s"Redis: $REDIS_HOST:$REDIS_PORT")
    println("等待 Kafka 消息...")

    try {
      val testJedis = new Jedis(REDIS_HOST, REDIS_PORT)
      testJedis.ping()
      testJedis.close()
      println("Redis 连接正常")
    } catch {
      case e: Exception => println(s"Redis 连接失败: ${e.getMessage}")
    }

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
      .foreachBatch { (batchDF: org.apache.spark.sql.Dataset[org.apache.spark.sql.Row], batchId: Long) =>
        val count = batchDF.count()
        println(s"=== 批次 $batchId: 收到 $count 条消息 ===")
        if (count > 0) {
          batchDF.collect().foreach { row =>
            try {
              val uid = row.get(0).toString.toLong
              val category = if (row.isNullAt(1)) "unknown" else row.getString(1)
              val likeType = if (row.isNullAt(2)) 0 else row.getInt(2)
              val viewTime = if (row.isNullAt(3)) 0.0 else row.getDouble(3)
              println(s"处理: userId=$uid, category=$category, likeType=$likeType, viewTime=$viewTime")

              val jedis = new Jedis(REDIS_HOST, REDIS_PORT)
              try {
                jedis.hincrBy(s"profile:$uid:cats", category, 1)
                jedis.hincrBy(s"profile:$uid:stats", "totalWatch", 1)
                if (likeType == 1) jedis.hincrBy(s"profile:$uid:stats", "totalLike", 1)
                jedis.hincrByFloat(s"profile:$uid:stats", "totalViewTime", viewTime)

                jedis.incr("dashboard:total_behaviors")
                jedis.zincrby("dashboard:category_counts", 1.0, category)
                val now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())
                val actionJson = s"""{"userId":$uid,"category":"$category","action":"${if (likeType == 1) "like" else "view"}","time":"$now"}"""
                jedis.lpush("dashboard:recent_actions", actionJson)
                jedis.ltrim("dashboard:recent_actions", 0, 49)
                println(s"Redis 写入成功: profile:$uid:cats, dashboard:total_behaviors")
              } catch {
                case e: Exception => println("ERROR: " + s"Redis 写入失败: ${e.getMessage}")
              } finally {
                jedis.close()
              }
            } catch {
              case e: Exception => println("ERROR: " + s"处理消息异常: ${e.getMessage}")
            }
          }
        }
      }
      .start()

    println("StreamingApp 已启动，等待消费...")
    query.awaitTermination()
  }
}
