package dev.dubhe.gugle.chat.channel.model;

import com.guglechat.common.enums.MemberRole;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "channel_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"channel_id", "user_id"}))
public class ChannelMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() { joinedAt = LocalDateTime.now(); }

    public ChannelMember() {}
    public ChannelMember(Long channelId, Long userId, MemberRole role) {
        this.channelId = channelId;
        this.userId = userId;
        this.role = role;
    }

    public Long getId() { return id; }
    public Long getChannelId() { return channelId; }
    public Long getUserId() { return userId; }
    public MemberRole getRole() { return role; }
    public void setRole(MemberRole role) { this.role = role; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
}
