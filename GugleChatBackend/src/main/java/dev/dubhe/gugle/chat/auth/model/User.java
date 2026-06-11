package dev.dubhe.gugle.chat.auth.model;

import com.baomidou.mybatisplus.annotation.*;
import dev.dubhe.gugle.chat.common.enums.UserRole;

import java.time.LocalDateTime;

@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField
    private String username;

    @TableField
    private String email;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField
    private String nickname;

    @TableField
    private UserRole role = UserRole.USER;

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

    public User() {}

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getFlag() { return flag; }
    public void setFlag(Integer flag) { this.flag = flag; }
}
