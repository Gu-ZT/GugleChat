package dev.dubhe.gugle.chat.message.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import dev.dubhe.gugle.chat.common.enums.MessageType;

import java.time.LocalDateTime;

@TableName("messages")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("channel_id")
    private Long channelId;

    @TableField("user_id")
    private Long userId;

    @TableField
    private String content;

    @TableField
    private MessageType type = MessageType.TEXT;

    @TableField("parent_id")
    private Long parentId;

    @TableField("edited_at")
    private LocalDateTime editedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Message() {}
    public Message(Long channelId, Long userId, String content, MessageType type) {
        this.channelId = channelId;
        this.userId = userId;
        this.content = content;
        this.type = type;
    }

    public Long getId() { return id; }
    public Long getChannelId() { return channelId; }
    public Long getUserId() { return userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public LocalDateTime getEditedAt() { return editedAt; }
    public void setEditedAt(LocalDateTime editedAt) { this.editedAt = editedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
