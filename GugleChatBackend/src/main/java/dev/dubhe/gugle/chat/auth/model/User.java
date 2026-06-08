package dev.dubhe.gugle.chat.auth.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import dev.dubhe.gugle.chat.common.enums.UserStatus;

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
    private UserStatus status = UserStatus.OFFLINE;

    @TableField("last_seen_at")
    private LocalDateTime lastSeenAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

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
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
