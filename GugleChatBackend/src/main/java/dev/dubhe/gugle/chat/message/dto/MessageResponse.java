package dev.dubhe.gugle.chat.message.dto;

import com.guglechat.common.enums.MessageType;
import com.guglechat.message.model.Message;
import java.time.LocalDateTime;
import java.util.List;

public class MessageResponse {
    private Long id;
    private Long channelId;
    private Long userId;
    private String username;
    private String content;
    private MessageType type;
    private Long parentId;
    private List<Long> fileIds;
    private LocalDateTime editedAt;
    private LocalDateTime createdAt;

    public static MessageResponse from(Message m, String username) {
        MessageResponse r = new MessageResponse();
        r.id = m.getId();
        r.channelId = m.getChannelId();
        r.userId = m.getUserId();
        r.username = username;
        r.content = m.getContent();
        r.type = m.getType();
        r.parentId = m.getParentId();
        r.editedAt = m.getEditedAt();
        r.createdAt = m.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getContent() { return content; }
    public MessageType getType() { return type; }
    public Long getParentId() { return parentId; }
    public List<Long> getFileIds() { return fileIds; }
    public LocalDateTime getEditedAt() { return editedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
