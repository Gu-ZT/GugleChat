package dev.dubhe.gugle.chat.channel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.dubhe.gugle.chat.auth.model.UserMapper;
import dev.dubhe.gugle.chat.channel.dto.*;
import dev.dubhe.gugle.chat.channel.model.*;
import dev.dubhe.gugle.chat.common.enums.MemberRole;
import dev.dubhe.gugle.chat.common.enums.UserRole;
import dev.dubhe.gugle.chat.common.exception.BusinessException;
import dev.dubhe.gugle.chat.signaling.service.RoomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChannelService {

    private final ChannelMapper channelMapper;
    private final ChannelMemberMapper memberMapper;
    private final RoomService roomService;
    private final UserMapper userMapper;

    public ChannelService(ChannelMapper channelMapper, ChannelMemberMapper memberMapper,
                          RoomService roomService, UserMapper userMapper) {
        this.channelMapper = channelMapper;
        this.memberMapper = memberMapper;
        this.roomService = roomService;
        this.userMapper = userMapper;
    }

    public List<ChannelResponse> getAllChannels(Long userId) {
        List<Channel> all = channelMapper.selectList(null);
        return all.stream()
                .map(c -> {
                    long count = memberMapper.selectCount(
                            new LambdaQueryWrapper<ChannelMember>().eq(ChannelMember::getChannelId, c.getId()));
                    boolean joined = memberMapper.existsByChannelIdAndUserId(c.getId(), userId);
                    var voiceUsers = c.getType() == dev.dubhe.gugle.chat.common.enums.ChannelType.VOICE
                            ? roomService.getRoomUsers(c.getId()) : null;
                    Long hostId = roomService.getHost(c.getId());
                    return ChannelResponse.from(c, (int) count, joined, voiceUsers, hostId);
                })
                .toList();
    }

    /** Auto-join a channel if not already a member */
    public void ensureMember(Long channelId, Long userId) {
        if (!memberMapper.existsByChannelIdAndUserId(channelId, userId)) {
            ChannelMember m = new ChannelMember(channelId, userId, MemberRole.MEMBER);
            m.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(m);
        }
    }

    @Transactional
    public ChannelResponse createChannel(Long userId, ChannelRequest req) {
        Channel c = new Channel(req.getName(), req.getType(), userId);
        c.setDescription(req.getDescription());
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        channelMapper.insert(c);

        ChannelMember m = new ChannelMember(c.getId(), userId, MemberRole.OWNER);
        m.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(m);

        return ChannelResponse.from(c, 1);
    }

    @Transactional
    public ChannelResponse updateChannel(Long channelId, Long userId, ChannelRequest req) {
        Channel c = channelMapper.selectById(channelId);
        if (c == null) throw new BusinessException("Channel not found");
        ensureAdmin(channelId, userId);
        c.setName(req.getName());
        c.setDescription(req.getDescription());
        c.setUpdatedAt(LocalDateTime.now());
        channelMapper.updateById(c);

        long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ChannelMember>().eq(ChannelMember::getChannelId, channelId));
        return ChannelResponse.from(c, (int) count);
    }

    @Transactional
    public void deleteChannel(Long channelId, Long userId) {
        Channel c = channelMapper.selectById(channelId);
        if (c == null) throw new BusinessException("Channel not found");
        if (!c.getCreatedBy().equals(userId)) {
            // Global admins can delete any channel
            var user = userMapper.selectById(userId);
            if (user == null || (user.getRole() != UserRole.SUPER_ADMIN && user.getRole() != UserRole.ADMIN))
                throw new BusinessException(403, "Only owner or global admin can delete");
        }
        memberMapper.delete(new LambdaQueryWrapper<ChannelMember>()
                .eq(ChannelMember::getChannelId, channelId));
        channelMapper.deleteById(channelId);
    }

    public List<MemberResponse> getMembers(Long channelId, Long userId) {
        ensureMember(channelId, userId);
        return memberMapper.selectList(
                new LambdaQueryWrapper<ChannelMember>().eq(ChannelMember::getChannelId, channelId))
                .stream().map(MemberResponse::from).toList();
    }

    @Transactional
    public void addMember(Long channelId, Long adderId, Long newUserId) {
        ensureAdmin(channelId, adderId);
        if (memberMapper.existsByChannelIdAndUserId(channelId, newUserId))
            throw new BusinessException("Already a member");
        ChannelMember m = new ChannelMember(channelId, newUserId, MemberRole.MEMBER);
        m.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(m);
    }

    @Transactional
    public void removeMember(Long channelId, Long removerId, Long targetUserId) {
        if (!removerId.equals(targetUserId))
            ensureAdmin(channelId, removerId);
        memberMapper.deleteByChannelIdAndUserId(channelId, targetUserId);
    }

    private void ensureAdmin(Long channelId, Long userId) {
        ChannelMember m = memberMapper.findByChannelIdAndUserId(channelId, userId);
        if (m == null) throw new BusinessException(403, "Not a member");
        if (m.getRole() == MemberRole.MEMBER) throw new BusinessException(403, "Admin+ required");
    }

}
