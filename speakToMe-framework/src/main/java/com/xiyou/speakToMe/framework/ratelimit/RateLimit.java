package com.xiyou.speakToMe.framework.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（基于 Redis Lua 原子实现）。
 *
 * 用法示例（评论提交接口：单用户每分钟最多 5 条）：
 *   @RateLimit(key = "rate:comment:{userId}", limit = 5, window = 60)
 *
 * key 支持占位符：{userId}（当前登录用户）、{ip}（客户端 IP）。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流键模板 */
    String key();

    /** 窗口内最大次数 */
    int limit() default 5;

    /** 窗口时长（秒） */
    int window() default 60;
}
