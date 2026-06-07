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
