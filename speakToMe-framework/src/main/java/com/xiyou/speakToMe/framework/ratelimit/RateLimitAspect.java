package com.xiyou.speakToMe.framework.ratelimit;

import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.framework.security.UserContext;
import com.xiyou.speakToMe.framework.util.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 限流切面：解析 @RateLimit，执行 Redis Lua 原子计数（INCR + 首次数设置 EXPIRE），
 * 超过阈值抛 30001。Lua 保证并发下计数与过期设置原子性，不会因并发绕过限流。
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    /**
     * Lua 脚本：计数器自增；首次自增时设置过期时间；返回当前计数。
     * KEYS[1] 限流键，ARGV[1] 窗口秒数。
     */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('incr', KEYS[1]) "
                    + "if c == 1 then redis.call('expire', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    private final StringRedisTemplate redis;

    public RateLimitAspect(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = resolveKey(joinPoint, rateLimit.key());
        Long count = redis.execute(RATE_LIMIT_SCRIPT, Collections.singletonList(key),
                String.valueOf(rateLimit.window()));
        long current = count == null ? 0L : count;
        if (current > rateLimit.limit()) {
            log.warn("触发限流 key={} count={} limit={}", key, current, rateLimit.limit());
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试");
        }
        return joinPoint.proceed();
    }

    /** 将 key 模板中的 {userId}/{ip} 占位符替换为真实值 */
    private String resolveKey(ProceedingJoinPoint joinPoint, String template) {
        String key = template;
        if (template.contains("{userId}")) {
            Long userId = UserContext.getUserId();
            if (userId == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
            }
            key = key.replace("{userId}", String.valueOf(userId));
        }
        if (template.contains("{ip}")) {
            key = key.replace("{ip}", IpUtil.getClientIp());
        }
        return key;
    }
}
