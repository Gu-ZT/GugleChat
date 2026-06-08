package dev.dubhe.gugle.chat.channel.model;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChannelMapper extends BaseMapper<Channel> {

    @Select("SELECT c.* FROM channels c JOIN channel_members cm ON c.id = cm.channel_id " +
            "WHERE cm.user_id = #{userId} ORDER BY c.updated_at DESC")
    List<Channel> findChannelsByUserId(Long userId);
}
