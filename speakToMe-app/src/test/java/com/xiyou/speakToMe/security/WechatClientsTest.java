package com.xiyou.speakToMe.security;

import cn.hutool.http.HttpException;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.content.client.WechatContentClient;
import com.xiyou.speakToMe.user.client.WechatClient;
import com.xiyou.speakToMe.user.config.WxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 10/21：只替换 HTTP 传输，执行真实微信客户端的请求构造、解析与异常逻辑。 */
class WechatClientsTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private WechatContentClient content;
    private WechatClient login;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("wx:access_token")).thenReturn("缓存凭证");
        content = new WechatContentClient(redis);
        ReflectionTestUtils.setField(content, "msgSecCheckUrl", "https://example.invalid/check");
        ReflectionTestUtils.setField(content, "appid", "测试应用");
        ReflectionTestUtils.setField(content, "secret", "测试密钥");
        WxProperties properties = new WxProperties();
        properties.setSessionUrl("https://example.invalid/login");
        properties.setAppid("测试应用");
        properties.setSecret("测试密钥");
        login = new WechatClient(properties);
    }

    @Test
    void loginSendsCodeAndReturnsOnlyOpenid() {
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(anyString(), anyMap(), eq(5000))).thenAnswer(invocation -> {
                java.util.Map<?, ?> params = invocation.getArgument(1);
                assertEquals("一次性凭证", params.get("js_code"));
                assertEquals("测试应用", params.get("appid"));
                assertEquals("authorization_code", params.get("grant_type"));
                return "{\"openid\":\"用户身份\",\"session_key\":\"不可返回的会话密钥\"}";
            });
            assertEquals("用户身份", login.code2Session("一次性凭证"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"errcode\":40029}", "{}", "", "<html>网关错误</html>", "{\"errcode\":\"错误\"}"})
    void invalidLoginResponseIsAlwaysBusinessFailure(String response) {
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(anyString(), anyMap(), eq(5000))).thenReturn(response);
            assertThrows(BizException.class, () -> login.code2Session("凭证"));
        }
    }

    @Test
    void loginTimeoutIsBusinessFailure() {
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(anyString(), anyMap(), eq(5000)))
                    .thenThrow(new HttpException("模拟超时"));
            assertThrows(BizException.class, () -> login.code2Session("凭证"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"pass", "review", "risky"})
    void validAuditResultsAndV2RequestArePreserved(String suggest) {
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString(), eq(5000))).thenAnswer(invocation -> {
                assertTrue(invocation.<String>getArgument(0).contains("access_token="));
                var body = JSONUtil.parseObj(invocation.<String>getArgument(1));
                assertEquals(2, body.getInt("version"));
                assertEquals(2, body.getInt("scene"));
                assertEquals("用户身份", body.getStr("openid"));
                assertEquals("点评正文", body.getStr("content"));
                assertEquals("123", body.getStr("clientMsgId"));
                return "{\"errcode\":0,\"result\":{\"suggest\":\"" + suggest + "\",\"label\":100}}";
            });
            var result = content.checkText("点评正文", "用户身份", "123");
            assertEquals("pass".equals(suggest), result.pass());
            assertEquals("review".equals(suggest), result.review());
            assertEquals("risky".equals(suggest), result.risky());
            assertEquals(100, result.label());
            http.verify(() -> HttpUtil.get(anyString(), anyMap(), anyInt()), never());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"errcode\":0}", "{\"result\":{}}", "{\"result\":{\"suggest\":\"未知值\"}}",
            "<html>网关错误</html>", "", "{\"errcode\":40001}", "{\"errcode\":\"错误\"}"})
    void incompleteAuditResponseCannotPassOrMarkUserAsRisky(String response) {
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString(), eq(5000))).thenReturn(response);
            assertThrows(BizException.class, () -> content.checkText("正文", "身份", "1"));
        }
    }

    @Test
    void auditTimeoutIsNormalizedForManualFallback() {
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString(), eq(5000)))
                    .thenThrow(new HttpException("模拟超时"));
            assertThrows(BizException.class, () -> content.checkText("正文", "身份", "1"));
        }
    }

    @Test
    void legacyRiskCodeStillBlocksContent() {
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString(), eq(5000)))
                    .thenReturn("{\"errcode\":87014}");
            assertTrue(content.checkText("正文", "身份", "1").risky());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {40001, 40014, 42001})
    void invalidOrExpiredTokenIsEvictedSoNextSubmissionCanRefresh(int errorCode) {
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString(), eq(5000)))
                    .thenReturn("{\"errcode\":" + errorCode + "}");
            assertThrows(BizException.class, () -> content.checkText("正文", "身份", "1"));
            verify(redis).delete("wx:access_token");
        }
    }

    @Test
    void tokenIsFetchedAndCachedOnlyOnMiss() {
        when(values.get("wx:access_token")).thenReturn(null);
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(anyString(), anyMap(), eq(5000)))
                    .thenReturn("{\"access_token\":\"新凭证\",\"expires_in\":7200}");
            http.when(() -> HttpUtil.post(anyString(), anyString(), eq(5000)))
                    .thenReturn("{\"errcode\":0,\"result\":{\"suggest\":\"review\"}}");
            assertTrue(content.checkText("正文", "身份", "1").review());
            verify(values).set("wx:access_token", "新凭证", Duration.ofSeconds(7000));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"errcode\":40164}", "<html>网关错误</html>"})
    void tokenFailureDoesNotSendContent(String response) {
        when(values.get("wx:access_token")).thenReturn(null);
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(anyString(), anyMap(), eq(5000))).thenReturn(response);
            assertThrows(BizException.class, () -> content.checkText("正文", "身份", "1"));
            http.verify(() -> HttpUtil.post(anyString(), anyString(), anyInt()), never());
            verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        }
    }
}
