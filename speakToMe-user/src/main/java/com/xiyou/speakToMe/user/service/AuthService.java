package com.xiyou.speakToMe.user.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.common.util.AesGcmUtil;
import com.xiyou.speakToMe.framework.security.SessionService;
import com.xiyou.speakToMe.user.client.WechatClient;
import com.xiyou.speakToMe.user.dto.LoginVO;
import com.xiyou.speakToMe.user.dto.UserVO;
import com.xiyou.speakToMe.user.entity.User;
import com.xiyou.speakToMe.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户与登录服务：
 * 1) wx.login code -> code2session 换 openid
 * 2) openid 密文+摘要双列存储（AES-256-GCM + SHA-256）
 * 3) Redis session 签发（可吊销）
 * 4) 校区选择、资料更新、登录态查询
 */
@Slf4j
@Service
public class AuthService {

    /** AES 密钥（环境变量注入），登录/加密场景必填 */
    @Value("${security.aes.key:}")
    private String aesKey;

    private final WechatClient wechatClient;
    private final UserMapper userMapper;
    private final SessionService sessionService;

    public AuthService(WechatClient wechatClient, UserMapper userMapper,
                       SessionService sessionService) {
        this.wechatClient = wechatClient;
        this.userMapper = userMapper;
        this.sessionService = sessionService;
    }

    /** 微信授权登录：不存在则注册新用户 */
    public LoginVO login(String code) {
        if (StrUtil.isBlank(code)) {
            throw new BizException(ErrorCode.BAD_PARAM, "登录凭证缺失");
        }
        String openid = wechatClient.code2Session(code);
        String hash = DigestUtil.sha256Hex(openid);

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenidHash, hash));
        if (user == null) {
            user = new User();
            user.setOpenidCipher(AesGcmUtil.encrypt(openid, aesKey));
            user.setOpenidHash(hash);
            user.setNickname("");
            user.setRole(0);
            user.setStatus(1);
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.insert(user);
            log.info("新用户注册 userId={}", user.getId());
        } else {
            if (user.getStatus() == null || user.getStatus() == 0) {
                throw new BizException(ErrorCode.FORBIDDEN, "账号已被封禁");
            }
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(user);
        }

        String token = sessionService.create(user.getId());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setNeedCampus(user.getCampusId() == null);
        vo.setUser(UserVO.from(user));
        return vo;
    }

    /** 选择/修改校区（存在性校验由 canteen 模块提供，此处做基础校验） */
    public UserVO setCampus(Long userId, Long campusId) {
        if (campusId == null || campusId <= 0) {
            throw new BizException(ErrorCode.BAD_PARAM, "校区参数错误");
        }
        User user = requireUser(userId);
        user.setCampusId(campusId);
        userMapper.updateById(user);
        return UserVO.from(user);
    }

    /** 更新昵称与头像：昵称 1-20 字（后续接入敏感词过滤） */
    public UserVO updateProfile(Long userId, String nickname, String avatarUrl) {
        if (StrUtil.isBlank(nickname) || nickname.trim().length() > 20) {
            throw new BizException(ErrorCode.BAD_PARAM, "昵称需为 1-20 个字符");
        }
        if (avatarUrl != null && avatarUrl.length() > 512) {
            throw new BizException(ErrorCode.BAD_PARAM, "头像地址过长");
        }
        User user = requireUser(userId);
        user.setNickname(nickname.trim());
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }
        userMapper.updateById(user);
        return UserVO.from(user);
    }

    /** 当前登录用户信息 */
    public UserVO me(Long userId) {
        return UserVO.from(requireUser(userId));
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号不存在或已被封禁");
        }
        return user;
    }
}
