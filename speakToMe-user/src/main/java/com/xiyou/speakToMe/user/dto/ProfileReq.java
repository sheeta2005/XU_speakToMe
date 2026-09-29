package com.xiyou.speakToMe.user.dto;

import lombok.Data;

/**
 * 资料更新请求：昵称与头像。
 */
@Data
public class ProfileReq {

    private String nickname;

    private String avatarUrl;
}
