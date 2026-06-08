package dev.dubhe.gugle.chat.channel.dto;

import com.guglechat.channel.model.Channel;
import com.guglechat.common.enums.ChannelType;
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

    public static ChannelResponse from(Channel channel, int memberCount) {
        ChannelResponse r = new ChannelResponse();
        r.id = channel.getId();
        r.name = channel.getName();
        r.description = channel.getDescription();
        r.type = channel.getType();
        r.createdBy = channel.getCreatedBy();
        r.memberCount = memberCount;
        r.createdAt = channel.getCreatedAt();
        r.updatedAt = channel.getUpdatedAt();
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
