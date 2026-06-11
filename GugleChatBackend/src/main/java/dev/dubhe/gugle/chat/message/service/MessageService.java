package dev.dubhe.gugle.chat.message.service;

import dev.dubhe.gugle.chat.auth.model.UserMapper;
import dev.dubhe.gugle.chat.channel.model.ChannelMember;
import dev.dubhe.gugle.chat.channel.model.ChannelMemberMapper;
import dev.dubhe.gugle.chat.channel.service.ChannelService;
import dev.dubhe.gugle.chat.common.enums.MemberRole;
import dev.dubhe.gugle.chat.common.enums.MessageType;
import dev.dubhe.gugle.chat.common.enums.UserRole;
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

    private static final int PAGE_SIZE = 15;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final ChannelService channelService;
    private final ChannelMemberMapper memberMapper;

    public MessageService(MessageMapper messageMapper, UserMapper userMapper,
                          ChannelService channelService, ChannelMemberMapper memberMapper) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.channelService = channelService;
        this.memberMapper = memberMapper;
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
        if (!msg.getUserId().equals(userId)) {
            // Global admins can delete any message
            var user = userMapper.selectById(userId);
            if (user != null && (user.getRole() == UserRole.SUPER_ADMIN || user.getRole() == UserRole.ADMIN)) {
                messageMapper.deleteById(messageId);
                return;
            }
            // Channel admins (OWNER/ADMIN) can delete messages in their channel
            ChannelMember member = memberMapper.findByChannelIdAndUserId(msg.getChannelId(), userId);
            if (member == null || member.getRole() == MemberRole.MEMBER)
                throw new BusinessException(403, "Can only delete your own messages");
        }
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
