package dev.dubhe.gugle.chat.channel.dto;

import dev.dubhe.gugle.chat.channel.model.ChannelMember;
import dev.dubhe.gugle.chat.common.enums.MemberRole;
import java.time.LocalDateTime;

public class MemberResponse {
    private Long id;
    private Long userId;
    private MemberRole role;
    private LocalDateTime joinedAt;

    public static MemberResponse from(ChannelMember m) {
        MemberResponse r = new MemberResponse();
        r.id = m.getId();
        r.userId = m.getUserId();
        r.role = m.getRole();
        r.joinedAt = m.getJoinedAt();
        return r;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public MemberRole getRole() { return role; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
}
