package dev.dubhe.gugle.chat.channel.model;

import com.baomidou.mybatisplus.annotation.*;
import dev.dubhe.gugle.chat.common.enums.MemberRole;

import java.time.LocalDateTime;

@TableName("channel_members")
public class ChannelMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("channel_id")
    private Long channelId;

    @TableField("user_id")
    private Long userId;

    @TableField
    private MemberRole role = MemberRole.MEMBER;

    @TableField("joined_at")
    private LocalDateTime joinedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @TableField
    private Integer version;

    @TableLogic
    @TableField
    private Integer flag;

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
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getFlag() { return flag; }
    public void setFlag(Integer flag) { this.flag = flag; }
}
