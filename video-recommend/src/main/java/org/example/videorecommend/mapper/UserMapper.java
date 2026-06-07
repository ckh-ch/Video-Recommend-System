package org.example.videorecommend.mapper;
import org.example.videorecommend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
@Mapper
public interface UserMapper {
    @Select("SELECT * FROM users WHERE id = #{id}")
    User selectById(Long id);
    @Select("SELECT * FROM users")
    List<User> selectAll();
}
