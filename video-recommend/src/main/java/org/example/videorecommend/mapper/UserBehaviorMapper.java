package org.example.videorecommend.mapper;
import org.example.videorecommend.entity.UserBehavior;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
@Mapper
public interface UserBehaviorMapper {
    @Select("SELECT * FROM user_behavior WHERE user_id = #{userId} ORDER BY behavior_time DESC LIMIT 100")
    List<UserBehavior> selectByUserId(Long userId);
    int insert(UserBehavior behavior);
}
