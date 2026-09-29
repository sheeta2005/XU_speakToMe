package com.xiyou.speakToMe.user.dto;

import lombok.Data;

/**
 * 登录请求：wx.login 返回的临时 code。
 */
@Data
public class LoginReq {

    private String code;
}
