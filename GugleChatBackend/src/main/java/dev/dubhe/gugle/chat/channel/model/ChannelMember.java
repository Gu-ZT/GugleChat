package dev.dubhe.gugle.chat.channel.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
}
