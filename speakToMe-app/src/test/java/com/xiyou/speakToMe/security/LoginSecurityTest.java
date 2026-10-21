package com.xiyou.speakToMe.security;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.common.util.AesGcmUtil;
import com.xiyou.speakToMe.framework.security.SessionService;
import com.xiyou.speakToMe.user.client.WechatClient;
import com.xiyou.speakToMe.user.entity.User;
import com.xiyou.speakToMe.user.mapper.UserMapper;
import com.xiyou.speakToMe.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 10/21：验证注册、再次登录、封禁以及敏感身份的加密边界。 */
class LoginSecurityTest {
    private final WechatClient wechat = mock(WechatClient.class);
    private final UserMapper users = mock(UserMapper.class);
    private final SessionService sessions = mock(SessionService.class);
    private final String key = AesGcmUtil.generateKey();
    private AuthService auth;

    @BeforeEach
    void setUp() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "登录测试"), User.class);
        auth = new AuthService(wechat, users, sessions);
        ReflectionTestUtils.setField(auth, "aesKey", key);
        when(wechat.code2Session("一次性凭证")).thenReturn("测试用户身份");
    }

    @Test
    void registrationStoresCipherAndHashAndReturnsNoSensitiveFields() {
        when(users.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertNotEquals("测试用户身份", user.getOpenidCipher());
            assertEquals("测试用户身份", AesGcmUtil.decrypt(user.getOpenidCipher(), key));
            assertEquals(DigestUtil.sha256Hex("测试用户身份"), user.getOpenidHash());
            assertEquals(0, user.getRole());
            assertEquals(1, user.getStatus());
            user.setId(42L);
            return 1;
        });
        when(sessions.create(42L)).thenReturn("测试会话");
        var login = auth.login("一次性凭证");
        assertEquals("测试会话", login.getToken());
        assertTrue(login.getNeedCampus());
        assertEquals(42L, login.getUser().getId());
        String response = cn.hutool.json.JSONUtil.toJsonStr(login);
        assertFalse(response.contains("openid"));
        assertFalse(response.contains("测试用户身份"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returningUserKeepsRoleCampusAndCipherWithoutDuplicateInsert() {
        User user = new User();
        user.setId(42L);
        user.setStatus(1);
        user.setRole(1);
        user.setCampusId(2L);
        user.setOpenidCipher("已有密文");
        when(users.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<User> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("openid_hash"));
            assertTrue(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) query)
                    .getParamNameValuePairs().containsValue(DigestUtil.sha256Hex("测试用户身份")));
            return user;
        });
        var login = auth.login("一次性凭证");
        assertFalse(login.getNeedCampus());
        assertEquals(1, login.getUser().getRole());
        assertEquals("已有密文", user.getOpenidCipher());
        assertNotNull(user.getLastLoginAt());
        verify(users, never()).insert(any(User.class));
        verify(users).updateById(user);
        verify(sessions).create(42L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bannedAccountCannotCreateSession() {
        User banned = new User();
        banned.setStatus(0);
        when(users.selectOne(any(Wrapper.class))).thenReturn(banned);
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(BizException.class, () -> auth.login("一次性凭证")).getCode());
        verifyNoInteractions(sessions);
        verify(users, never()).updateById(any(User.class));
    }

    @Test
    void emptyOrFailedWechatLoginDoesNotCreateUserOrSession() {
        assertEquals(ErrorCode.BAD_PARAM,
                assertThrows(BizException.class, () -> auth.login(" ")).getCode());
        verifyNoInteractions(wechat, users, sessions);
        when(wechat.code2Session("失效凭证")).thenThrow(new BizException(ErrorCode.SYSTEM_ERROR, "登录失败"));
        assertThrows(BizException.class, () -> auth.login("失效凭证"));
        verifyNoInteractions(users, sessions);
    }

    @Test
    void encryptionUsesRandomIvAndRejectsTamperingAndWrongKeys() {
        String first = AesGcmUtil.encrypt("中文身份标识", key);
        String second = AesGcmUtil.encrypt("中文身份标识", key);
        assertNotEquals(first, second);
        assertEquals("中文身份标识", AesGcmUtil.decrypt(first, key));
        byte[] payload = Base64.getDecoder().decode(first);
        payload[payload.length - 1] ^= 1;
        assertThrows(IllegalStateException.class,
                () -> AesGcmUtil.decrypt(Base64.getEncoder().encodeToString(payload), key));
        assertThrows(IllegalStateException.class, () -> AesGcmUtil.decrypt(first, AesGcmUtil.generateKey()));
        assertThrows(IllegalStateException.class, () -> AesGcmUtil.decrypt("损坏密文", key));
    }
}
