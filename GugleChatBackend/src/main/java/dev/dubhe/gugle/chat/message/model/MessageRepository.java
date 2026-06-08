package dev.dubhe.gugle.chat.message.model;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByChannelIdAndIdBeforeOrderByCreatedAtDesc(Long channelId, Long beforeId, Pageable pageable);
    List<Message> findByChannelIdOrderByCreatedAtDesc(Long channelId, Pageable pageable);
}
