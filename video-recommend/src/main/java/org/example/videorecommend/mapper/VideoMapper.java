package org.example.videorecommend.mapper;
import org.example.videorecommend.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
@Mapper
public interface VideoMapper {
    @Select("SELECT * FROM videos WHERE category = #{category} LIMIT #{limit}")
    List<Video> selectByCategory(String category, int limit);
    @Select("SELECT * FROM videos ORDER BY view_count DESC LIMIT #{limit}")
    List<Video> selectHotVideos(int limit);
    @Select("<script> SELECT * FROM videos WHERE id IN <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach> </script>")
    List<Video> selectByIds(@Param("ids") List<Long> ids);
}
