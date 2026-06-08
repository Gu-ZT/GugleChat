package dev.dubhe.gugle.chat.channel.dto;

import com.guglechat.common.enums.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ChannelRequest {
    @NotBlank
    private String name;
    private String description;
    @NotNull
    private ChannelType type;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ChannelType getType() { return type; }
    public void setType(ChannelType type) { this.type = type; }
}
