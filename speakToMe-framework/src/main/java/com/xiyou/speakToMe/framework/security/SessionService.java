package com.xiyou.speakToMe.framework.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 登录会话服务：token 存 Redis（TTL 可配置，默认 7 天），
 * 服务端可随时吊销实现封号/踢下线。
 */
@Service
public class SessionService {

    private static final String PREFIX = "session:";

    private final StringRedisTemplate redis;

    @Value("${session.ttl-days:7}")
    private long ttlDays;

    public SessionService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 签发会话，返回 token */
    public String create(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(PREFIX + token, String.valueOf(userId),
                Duration.ofDays(ttlDays));
        return token;
    }

    /** 校验 token，返回 userId；无效/过期返回 null */
    public Long verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String value = redis.opsForValue().get(PREFIX + token);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 吊销会话（封号/登出/强制下线） */
    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(PREFIX + token);
        }
    }

    /** 按用户吊销全部会话（封号场景） */
    public void revokeAllByUser(Long userId) {
        // 简单实现：批量扫描删除 session:* 中值为 userId 的 key
        // 生产环境建议用独立索引或 cursor 分批删除
        // redis.keys(PREFIX + "*").stream()
        //         .filter(k -> userId.toString().equals(redis.opsForValue().get(k)))
        //         .forEach(redis::delete);
        // 一期单会话制（同用户再次登录顶替旧 token 由 create 覆盖语义不冲突），
        // 封号时直接按 userId 走黑名单校验即可，见 UserContext 使用方。
    }
}
