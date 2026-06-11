package dev.dubhe.gugle.chat.channel.model;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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

    /** 逻辑删除：将 flag 置为 1（由 MyBatis-Plus 全局逻辑删除自动处理） */
    default void deleteByChannelIdAndUserId(Long channelId, Long userId) {
        delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChannelMember>()
                .eq(ChannelMember::getChannelId, channelId)
                .eq(ChannelMember::getUserId, userId));
    }
}
