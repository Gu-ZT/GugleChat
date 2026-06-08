package dev.dubhe.gugle.chat.channel.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {
    List<ChannelMember> findByChannelId(Long channelId);
    Optional<ChannelMember> findByChannelIdAndUserId(Long channelId, Long userId);
    boolean existsByChannelIdAndUserId(Long channelId, Long userId);
    void deleteByChannelIdAndUserId(Long channelId, Long userId);
}
