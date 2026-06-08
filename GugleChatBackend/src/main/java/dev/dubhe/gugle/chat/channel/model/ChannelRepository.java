package dev.dubhe.gugle.chat.channel.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    @Query("SELECT c FROM Channel c JOIN ChannelMember cm ON c.id = cm.channelId " +
           "WHERE cm.userId = :userId ORDER BY c.updatedAt DESC")
    List<Channel> findChannelsByUserId(@Param("userId") Long userId);
}
