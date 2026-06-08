package dev.dubhe.gugle.chat.message.model;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("SELECT * FROM messages WHERE channel_id = #{channelId} " +
            "AND id < #{beforeId} ORDER BY created_at DESC LIMIT #{limit}")
    List<Message> findByChannelIdBefore(Long channelId, Long beforeId, int limit);

    @Select("SELECT * FROM messages WHERE channel_id = #{channelId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<Message> findByChannelId(Long channelId, int limit);
}
