package com.xiyou.speakToMe.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.framework.security.LoginInterceptor;
import com.xiyou.speakToMe.framework.security.SessionService;
import com.xiyou.speakToMe.framework.security.UserContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** 10/21：仅连接显式指定的独立测试 Redis；CI 必须启用，绝不清空整个数据库。 */
@EnabledIfEnvironmentVariable(named = "TEST_REDIS_PORT", matches = "\\d+")
class RedisSecurityIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private SessionService sessions;
    private final List<String> keys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(System.getenv("TEST_REDIS_PORT")));
        String password = System.getenv("TEST_REDIS_PASSWORD");
        if (password != null && !password.isBlank()) config.setPassword(password);
        factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        sessions = new SessionService(redis);
        ReflectionTestUtils.setField(sessions, "ttlDays", 7L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        if (redis != null && !keys.isEmpty()) redis.delete(keys);
        if (factory != null) factory.destroy();
    }

    @Test
    void sessionHasTtlCanBeRevokedAndDoesNotExposeUserIdInToken() {
        String first = sessions.create(42L);
        String second = sessions.create(42L);
        keys.add("session:" + first);
        keys.add("session:" + second);
        assertTrue(first.matches("[a-f0-9]{32}"));
        assertNotEquals(first, second);
        assertEquals(42L, sessions.verify(first));
        assertTrue(redis.getExpire("session:" + first) > Duration.ofDays(6).toSeconds());
        sessions.revoke(first);
        assertNull(sessions.verify(first));
        assertEquals(42L, sessions.verify(second));
        assertNull(sessions.verify(" "));
        assertNull(sessions.verify("不存在的会话"));
    }

    @Test
    void expiredOrMalformedSessionCannotAuthenticate() {
        String token = UUID.randomUUID().toString();
        String key = "session:" + token;
        keys.add(key);
        redis.opsForValue().set(key, "不是用户编号", Duration.ofSeconds(2));
        assertNull(sessions.verify(token));
        redis.opsForValue().set(key, "42", Duration.ofMillis(150));
        await().atMost(Duration.ofSeconds(3)).until(() -> sessions.verify(token) == null);
    }

    @Test
    void interceptorUsesRealSessionAndClearsThreadContextEvenAfterFailure() throws Exception {
        LoginInterceptor interceptor = new LoginInterceptor(sessions, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(response.getContentAsString().contains("10001"));
        String token = sessions.create(42L);
        keys.add("session:" + token);
        request.addHeader("Authorization", "Bearer " + token);
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(42L, UserContext.getUserId());
        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), new RuntimeException());
        assertNull(UserContext.getUserId());
        sessions.revoke(token);
        response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(response.getContentAsString().contains("10002"));
        assertNull(UserContext.getUserId());
    }

    @Test
    void concurrentLuaCounterAllowsExactlyFiveAndRecoversAfterExpiry() throws Exception {
        // 10/21：每次使用独立用户键，40 个并发请求通过真实切面竞争同一 Redis 计数器。
        long userId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        String key = "test:comment:" + userId;
        keys.add(key);
        var endpoint = RateLimitTest.proxy(redis);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(12)) {
            for (int i = 0; i < 40; i++) {
                results.add(pool.submit(() -> {
                    UserContext.set(userId, "并发测试会话");
                    try {
                        endpoint.comment();
                        return true;
                    } catch (BizException exception) {
                        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.getCode());
                        return false;
                    } finally {
                        UserContext.clear();
                    }
                }));
            }
            int accepted = 0;
            for (Future<Boolean> result : results) if (result.get(10, TimeUnit.SECONDS)) accepted++;
            assertEquals(5, accepted);
        }
        assertEquals("40", redis.opsForValue().get(key));
        assertTrue(redis.getExpire(key) > 0 && redis.getExpire(key) <= 60);
        redis.expire(key, Duration.ofMillis(150));
        await().atMost(Duration.ofSeconds(3)).until(() -> !Boolean.TRUE.equals(redis.hasKey(key)));
        UserContext.set(userId, "测试会话");
        endpoint.comment();
        assertEquals("1", redis.opsForValue().get(key));
        assertTrue(redis.getExpire(key) > 0);
        UserContext.set(userId + 1, "另一用户");
        keys.add("test:comment:" + (userId + 1));
        endpoint.comment();
        assertEquals("1", redis.opsForValue().get("test:comment:" + (userId + 1)));
    }
}
