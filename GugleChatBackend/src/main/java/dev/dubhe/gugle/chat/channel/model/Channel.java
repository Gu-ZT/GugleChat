package dev.dubhe.gugle.chat.channel.model;

import com.baomidou.mybatisplus.annotation.*;
import dev.dubhe.gugle.chat.common.enums.ChannelType;

import java.time.LocalDateTime;

@TableName("channels")
public class Channel {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField
    private String name;

    @TableField
    private String description;

    @TableField
    private ChannelType type = ChannelType.TEXT;

    @TableField("created_by")
    private Long createdBy;

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

    public Channel() {}
    public Channel(String name, ChannelType type, Long createdBy) {
        this.name = name;
        this.type = type;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ChannelType getType() { return type; }
    public void setType(ChannelType type) { this.type = type; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getFlag() { return flag; }
    public void setFlag(Integer flag) { this.flag = flag; }
}
