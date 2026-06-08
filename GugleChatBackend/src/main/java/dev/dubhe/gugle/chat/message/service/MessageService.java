package dev.dubhe.gugle.chat.message.service;

import dev.dubhe.gugle.chat.auth.model.UserMapper;
import dev.dubhe.gugle.chat.channel.service.ChannelService;
import dev.dubhe.gugle.chat.common.enums.MessageType;
import dev.dubhe.gugle.chat.common.exception.BusinessException;
import dev.dubhe.gugle.chat.message.dto.MessageResponse;
import dev.dubhe.gugle.chat.message.dto.SendMessageRequest;
import dev.dubhe.gugle.chat.message.model.Message;
import dev.dubhe.gugle.chat.common.util.XssFilter;
import dev.dubhe.gugle.chat.message.model.MessageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    private static final int PAGE_SIZE = 50;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final ChannelService channelService;

    public MessageService(MessageMapper messageMapper, UserMapper userMapper,
                          ChannelService channelService) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.channelService = channelService;
    }

    public MessageResponse sendMessage(Long channelId, Long userId, SendMessageRequest req) {
        channelService.ensureMember(channelId, userId); // auto-join
        MessageType type = req.getType() != null ? req.getType() : MessageType.TEXT;
        String safe = XssFilter.sanitize(req.getContent());
        Message msg = new Message(channelId, userId, safe, type);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        return MessageResponse.from(msg, getUsername(userId));
    }

    public List<MessageResponse> getHistory(Long channelId, Long userId, Long beforeId) {
        channelService.ensureMember(channelId, userId); // auto-join
        List<Message> messages = beforeId != null
                ? messageMapper.findByChannelIdBefore(channelId, beforeId, PAGE_SIZE)
                : messageMapper.findByChannelId(channelId, PAGE_SIZE);
        return messages.stream().map(m -> MessageResponse.from(m, getUsername(m.getUserId()))).toList();
    }

    public MessageResponse editMessage(Long messageId, Long userId, String newContent) {
        Message msg = messageMapper.selectById(messageId);
        if (msg == null) throw new BusinessException("Message not found");
        if (!msg.getUserId().equals(userId))
            throw new BusinessException(403, "Can only edit your own messages");
        msg.setContent(XssFilter.sanitize(newContent));
        msg.setEditedAt(LocalDateTime.now());
        messageMapper.updateById(msg);
        return MessageResponse.from(msg, getUsername(userId));
    }

    public void deleteMessage(Long messageId, Long userId) {
        Message msg = messageMapper.selectById(messageId);
        if (msg == null) throw new BusinessException("Message not found");
        if (!msg.getUserId().equals(userId))
            throw new BusinessException(403, "Can only delete your own messages");
        messageMapper.deleteById(messageId);
    }

    public Long getChannelId(Long messageId) {
        Message msg = messageMapper.selectById(messageId);
        if (msg == null) throw new BusinessException("Message not found");
        return msg.getChannelId();
    }

    private String getUsername(Long userId) {
        var user = userMapper.selectById(userId);
        return user != null ? user.getUsername() : "Unknown";
    }
}
