package dev.dubhe.gugle.chat.channel.service;

import com.guglechat.channel.dto.*;
import com.guglechat.channel.model.*;
import com.guglechat.common.enums.MemberRole;
import com.guglechat.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ChannelService {

    private final ChannelRepository channelRepo;
    private final ChannelMemberRepository memberRepo;

    public ChannelService(ChannelRepository channelRepo, ChannelMemberRepository memberRepo) {
        this.channelRepo = channelRepo;
        this.memberRepo = memberRepo;
    }

    public List<ChannelResponse> getUserChannels(Long userId) {
        return channelRepo.findChannelsByUserId(userId).stream()
                .map(c -> ChannelResponse.from(c, memberRepo.findByChannelId(c.getId()).size()))
                .toList();
    }

    @Transactional
    public ChannelResponse createChannel(Long userId, ChannelRequest req) {
        Channel c = new Channel(req.getName(), req.getType(), userId);
        c.setDescription(req.getDescription());
        c = channelRepo.save(c);
        memberRepo.save(new ChannelMember(c.getId(), userId, MemberRole.OWNER));
        return ChannelResponse.from(c, 1);
    }

    @Transactional
    public ChannelResponse updateChannel(Long channelId, Long userId, ChannelRequest req) {
        Channel c = channelRepo.findById(channelId)
                .orElseThrow(() -> new BusinessException("Channel not found"));
        ensureAdmin(channelId, userId);
        c.setName(req.getName());
        c.setDescription(req.getDescription());
        c = channelRepo.save(c);
        return ChannelResponse.from(c, memberRepo.findByChannelId(channelId).size());
    }

    @Transactional
    public void deleteChannel(Long channelId, Long userId) {
        Channel c = channelRepo.findById(channelId)
                .orElseThrow(() -> new BusinessException("Channel not found"));
        if (!c.getCreatedBy().equals(userId))
            throw new BusinessException(403, "Only owner can delete");
        memberRepo.findByChannelId(channelId).forEach(m -> memberRepo.delete(m));
        channelRepo.delete(c);
    }

    public List<MemberResponse> getMembers(Long channelId, Long userId) {
        ensureMember(channelId, userId);
        return memberRepo.findByChannelId(channelId).stream().map(MemberResponse::from).toList();
    }

    @Transactional
    public void addMember(Long channelId, Long adderId, Long newUserId) {
        ensureAdmin(channelId, adderId);
        if (memberRepo.existsByChannelIdAndUserId(channelId, newUserId))
            throw new BusinessException("Already a member");
        memberRepo.save(new ChannelMember(channelId, newUserId, MemberRole.MEMBER));
    }

    @Transactional
    public void removeMember(Long channelId, Long removerId, Long targetUserId) {
        if (!removerId.equals(targetUserId))
            ensureAdmin(channelId, removerId);
        memberRepo.deleteByChannelIdAndUserId(channelId, targetUserId);
    }

    private void ensureAdmin(Long channelId, Long userId) {
        ChannelMember m = memberRepo.findByChannelIdAndUserId(channelId, userId)
                .orElseThrow(() -> new BusinessException(403, "Not a member"));
        if (m.getRole() == MemberRole.MEMBER)
            throw new BusinessException(403, "Admin+ required");
    }

    private void ensureMember(Long channelId, Long userId) {
        if (!memberRepo.existsByChannelIdAndUserId(channelId, userId))
            throw new BusinessException(403, "Not a member");
    }
}
