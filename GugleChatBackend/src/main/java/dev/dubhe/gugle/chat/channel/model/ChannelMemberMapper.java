package dev.dubhe.gugle.chat.channel.model;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChannelMemberMapper extends BaseMapper<ChannelMember> {

    default ChannelMember findByChannelIdAndUserId(Long channelId, Long userId) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChannelMember>()
                .eq(ChannelMember::getChannelId, channelId)
                .eq(ChannelMember::getUserId, userId));
    }

    default boolean existsByChannelIdAndUserId(Long channelId, Long userId) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChannelMember>()
                .eq(ChannelMember::getChannelId, channelId)
                .eq(ChannelMember::getUserId, userId)) > 0;
    }

    @Delete("DELETE FROM channel_members WHERE channel_id = #{channelId} AND user_id = #{userId}")
    void deleteByChannelIdAndUserId(Long channelId, Long userId);
}
