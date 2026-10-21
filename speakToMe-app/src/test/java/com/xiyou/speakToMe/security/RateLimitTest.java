package com.xiyou.speakToMe.security;

import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.framework.ratelimit.RateLimit;
import com.xiyou.speakToMe.framework.ratelimit.RateLimitAspect;
import com.xiyou.speakToMe.framework.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 10/21：执行真实 AOP 代理，验证超额、身份缺失、代理头伪造和计数异常。 */
class RateLimitTest {
    public static class Endpoints {
        int calls;
        @RateLimit(key = "test:comment:{userId}", limit = 5, window = 60)
        public int comment() { return ++calls; }
        @RateLimit(key = "test:login:{ip}", limit = 5, window = 60)
        public int login() { return ++calls; }
    }

    static Endpoints proxy(StringRedisTemplate redis) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new Endpoints());
        factory.addAspect(new RateLimitAspect(redis));
        return factory.getProxy();
    }

    @AfterEach
    void cleanContext() {
        UserContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @SuppressWarnings("unchecked")
    void limitAllowsBoundaryAndRejectsNextCallBeforeBusinessLogic() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), eq(List.of("test:comment:42")), eq("60")))
                .thenReturn(5L, 6L);
        UserContext.set(42L, "测试会话");
        Endpoints endpoints = proxy(redis);
        assertEquals(1, endpoints.comment());
        assertEquals(ErrorCode.TOO_MANY_REQUESTS,
                assertThrows(BizException.class, endpoints::comment).getCode());
    }

    @Test
    void missingUserNeverTouchesRedisOrBusinessLogic() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        assertEquals(ErrorCode.UNAUTHORIZED,
                assertThrows(BizException.class, proxy(redis)::comment).getCode());
        verifyNoInteractions(redis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void directRequestsCannotChangeRateKeyUsingForwardedHeaders() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("60"))).thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        request.addHeader("X-Real-IP", "198.51.100.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals(1, proxy(redis).login());
        verify(redis).execute(any(RedisScript.class), eq(List.of("test:login:192.0.2.10")), eq("60"));
    }

    @Test
    void nullCounterDoesNotBypassRateLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        UserContext.set(42L, "测试会话");
        assertEquals(ErrorCode.SYSTEM_ERROR,
                assertThrows(BizException.class, proxy(redis)::comment).getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisOutageDoesNotExecuteBusinessLogic() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("60")))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("模拟断连"));
        UserContext.set(42L, "测试会话");
        assertThrows(org.springframework.data.redis.RedisConnectionFailureException.class, proxy(redis)::comment);
    }
}
