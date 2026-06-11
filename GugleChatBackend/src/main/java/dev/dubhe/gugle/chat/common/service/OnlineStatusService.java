package dev.dubhe.gugle.chat.common.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户在线状态由 Redis 维护，TTL 30 秒。
 * 客户端通过心跳持续刷新，掉线后自动过期。
 */
@Service
public class OnlineStatusService {

    private static final String KEY_PREFIX = "online:user:";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;

    public OnlineStatusService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 标记用户在线（每次心跳调用） */
    public void heartbeat(Long userId, String username) {
        redis.opsForValue().set(KEY_PREFIX + userId, username, TTL);
    }

    /** 立即下线 */
    public void setOffline(Long userId) {
        redis.delete(KEY_PREFIX + userId);
    }

    /** 查询用户是否在线 */
    public boolean isOnline(Long userId) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + userId));
    }

    /** 获取所有在线用户 ID */
    public Set<Long> getOnlineUserIds() {
        return redis.keys(KEY_PREFIX + "*").stream()
                .map(k -> Long.parseLong(k.substring(KEY_PREFIX.length())))
                .collect(Collectors.toSet());
    }
}
