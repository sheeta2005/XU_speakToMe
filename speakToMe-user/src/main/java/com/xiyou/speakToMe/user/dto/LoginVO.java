package com.xiyou.speakToMe.user.dto;

import lombok.Data;

/**
 * 登录响应：会话 token + 用户信息。
 * needCampus=true 时前端跳转校区选择页。
 */
@Data
public class LoginVO {

    private String token;

    /** 首次登录（未选校区）时为 true */
    private Boolean needCampus;

    private UserVO user;
}
