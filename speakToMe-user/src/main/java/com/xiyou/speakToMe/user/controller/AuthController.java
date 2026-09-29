package com.xiyou.speakToMe.user.controller;

import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.framework.ratelimit.RateLimit;
import com.xiyou.speakToMe.framework.result.Result;
import com.xiyou.speakToMe.framework.security.UserContext;
import com.xiyou.speakToMe.user.dto.CampusReq;
import com.xiyou.speakToMe.user.dto.LoginReq;
import com.xiyou.speakToMe.user.dto.LoginVO;
import com.xiyou.speakToMe.user.dto.ProfileReq;
import com.xiyou.speakToMe.user.dto.UserVO;
import com.xiyou.speakToMe.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权与用户接口（/api/v1/auth/**）。
 * 登录接口放行；其余接口经 LoginInterceptor 校验登录态。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 微信授权登录。
     * 登录限流：单 IP 每分钟 5 次，防止凭证接口被脚本爆破。
     */
    @PostMapping("/login")
    @RateLimit(key = "rate:login:{ip}", limit = 5, window = 60)
    public Result<LoginVO> login(@RequestBody LoginReq req) {
        return Result.ok(authService.login(req.getCode()));
    }

    /** 首次登录选择校区（后续可修改） */
    @PutMapping("/campus")
    public Result<UserVO> campus(@RequestBody CampusReq req) {
        return Result.ok(authService.setCampus(requireUserId(), req.getCampusId()));
    }

    /** 更新昵称与头像 */
    @PutMapping("/profile")
    public Result<UserVO> profile(@RequestBody ProfileReq req) {
        return Result.ok(authService.updateProfile(requireUserId(), req.getNickname(), req.getAvatarUrl()));
    }

    /** 当前登录用户信息（小程序启动时调用） */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.me(requireUserId()));
    }

    /** 从登录上下文取用户，未登录由拦截器统一拦截，此处兜底 */
    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
