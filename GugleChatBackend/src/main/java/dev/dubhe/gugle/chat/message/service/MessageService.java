package dev.dubhe.gugle.chat.message.service;

import com.guglechat.auth.model.User;
import com.guglechat.auth.model.UserRepository;
import com.guglechat.common.enums.MessageType;
import com.guglechat.common.exception.BusinessException;
import com.guglechat.message.dto.MessageResponse;
import com.guglechat.message.dto.SendMessageRequest;
import com.guglechat.message.model.Message;
import com.guglechat.message.model.MessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MessageService {

    private static final int PAGE_SIZE = 50;
    private final MessageRepository messageRepo;
    private final UserRepository userRepo;

    public MessageService(MessageRepository messageRepo, UserRepository userRepo) {
        this.messageRepo = messageRepo;
        this.userRepo = userRepo;
    }

    public MessageResponse sendMessage(Long channelId, Long userId, SendMessageRequest req) {
        MessageType type = req.getType() != null ? req.getType() : MessageType.TEXT;
        Message msg = new Message(channelId, userId, req.getContent(), type);
        msg = messageRepo.save(msg);
        return MessageResponse.from(msg, getUsername(userId));
    }

    public List<MessageResponse> getHistory(Long channelId, Long beforeId) {
        Pageable pageable = PageRequest.of(0, PAGE_SIZE);
        List<Message> messages = beforeId != null
                ? messageRepo.findByChannelIdAndIdBeforeOrderByCreatedAtDesc(channelId, beforeId, pageable)
                : messageRepo.findByChannelIdOrderByCreatedAtDesc(channelId, pageable);
        return messages.stream().map(m -> MessageResponse.from(m, getUsername(m.getUserId()))).toList();
    }

    public MessageResponse editMessage(Long messageId, Long userId, String newContent) {
        Message msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new BusinessException("Message not found"));
        if (!msg.getUserId().equals(userId))
            throw new BusinessException(403, "Can only edit your own messages");
        msg.setContent(newContent);
        msg.setEditedAt(java.time.LocalDateTime.now());
        msg = messageRepo.save(msg);
        return MessageResponse.from(msg, getUsername(userId));
    }

    public void deleteMessage(Long messageId, Long userId) {
        Message msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new BusinessException("Message not found"));
        if (!msg.getUserId().equals(userId))
            throw new BusinessException(403, "Can only delete your own messages");
        messageRepo.delete(msg);
    }

    public Long getChannelId(Long messageId) {
        return messageRepo.findById(messageId)
                .orElseThrow(() -> new BusinessException("Message not found")).getChannelId();
    }

    private String getUsername(Long userId) {
        return userRepo.findById(userId).map(User::getUsername).orElse("Unknown");
    }
}
