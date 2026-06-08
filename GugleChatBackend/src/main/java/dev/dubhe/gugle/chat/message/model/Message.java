package dev.dubhe.gugle.chat.message.model;

import com.guglechat.common.enums.MessageType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages", indexes = @Index(name = "idx_channel_created", columnList = "channel_id, created_at"))
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type = MessageType.TEXT;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

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
}
