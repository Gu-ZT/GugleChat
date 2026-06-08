package dev.dubhe.gugle.chat.channel.dto;

import dev.dubhe.gugle.chat.channel.model.Channel;
import dev.dubhe.gugle.chat.common.enums.ChannelType;
import java.time.LocalDateTime;

public class ChannelResponse {
    private Long id;
    private String name;
    private String description;
    private ChannelType type;
    private Long createdBy;
    private int memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ChannelResponse from(Channel c, int memberCount) {
        ChannelResponse r = new ChannelResponse();
        r.id = c.getId();
        r.name = c.getName();
        r.description = c.getDescription();
        r.type = c.getType();
        r.createdBy = c.getCreatedBy();
        r.memberCount = memberCount;
        r.createdAt = c.getCreatedAt();
        r.updatedAt = c.getUpdatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ChannelType getType() { return type; }
    public Long getCreatedBy() { return createdBy; }
    public int getMemberCount() { return memberCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
