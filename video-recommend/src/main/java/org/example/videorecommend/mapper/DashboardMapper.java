package org.example.videorecommend.mapper;
import org.example.videorecommend.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM videos")
    Long countVideos();

    @Select("SELECT COUNT(*) FROM users")
    Long countUsers();

    @Select("SELECT COUNT(*) FROM user_behavior")
    Long countBehaviors();

    @Select("SELECT COUNT(DISTINCT category) FROM videos")
    Long countCategories();

    @Select("SELECT category AS name, COUNT(*) AS value FROM videos GROUP BY category ORDER BY value DESC")
    List<CategoryDist> categoryDistribution();

    @Select("SELECT active_level AS level, COUNT(*) AS count FROM user_profile GROUP BY active_level ORDER BY level DESC")
    List<ActivityDist> activityDistribution();

    @Select("SELECT video_category AS category, AVG(viewing_time) AS avgViewTime, AVG(like_type) AS likeRate, AVG(relay_type) AS relayRate, COUNT(*) AS behaviorCount FROM user_behavior GROUP BY video_category ORDER BY behaviorCount DESC")
    List<BehaviorStat> behaviorStats();

    @Select("SELECT HOUR(behavior_time) AS hour, COUNT(*) AS count FROM user_behavior GROUP BY HOUR(behavior_time) ORDER BY hour")
    List<HourlyTrend> hourlyTrend();

    @Select("SELECT COUNT(*) FROM recommend_results")
    Long countRecommends();

    @Select("SELECT video_category AS name, COUNT(*) AS value FROM user_behavior WHERE user_id = #{userId} GROUP BY video_category ORDER BY value DESC")
    List<CategoryDist> userCategoryInterests(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM user_behavior WHERE user_id = #{userId}")
    Long countUserBehaviors(@Param("userId") Long userId);

    @Select("SELECT COUNT(DISTINCT video_category) FROM user_behavior WHERE user_id = #{userId}")
    Long countUserCategories(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(viewing_time), 0) FROM user_behavior WHERE user_id = #{userId}")
    Double sumUserViewTime(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(like_type), 0) FROM user_behavior WHERE user_id = #{userId} AND like_type = 1")
    Long sumUserLikes(@Param("userId") Long userId);

    @Select("SELECT video_category AS name, COUNT(*) AS value FROM user_behavior WHERE user_id = #{userId} GROUP BY video_category ORDER BY value DESC")
    List<CategoryDist> userCategoryDistribution(@Param("userId") Long userId);

    @Select("SELECT video_category AS category, AVG(viewing_time) AS avgViewTime, AVG(like_type) AS likeRate, AVG(relay_type) AS relayRate, COUNT(*) AS behaviorCount FROM user_behavior WHERE user_id = #{userId} GROUP BY video_category ORDER BY behaviorCount DESC")
    List<BehaviorStat> userBehaviorStats(@Param("userId") Long userId);

    @Select("SELECT HOUR(behavior_time) AS hour, COUNT(*) AS count FROM user_behavior WHERE user_id = #{userId} GROUP BY HOUR(behavior_time) ORDER BY hour")
    List<HourlyTrend> userHourlyTrend(@Param("userId") Long userId);
}
