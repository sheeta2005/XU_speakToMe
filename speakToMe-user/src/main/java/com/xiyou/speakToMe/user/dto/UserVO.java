package com.xiyou.speakToMe.user.dto;

import com.xiyou.speakToMe.user.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息视图（对外不暴露任何敏感字段）。
 */
@Data
public class UserVO {

    private Long id;

    private String nickname;

    private String avatarUrl;

    private Long campusId;

    /** 0 普通用户 1 管理员 */
    private Integer role;

    private LocalDateTime createdAt;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        // 未设置昵称时给默认展示名
        vo.setNickname(user.getNickname() == null || user.getNickname().isBlank()
                ? "西邮同学" : user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setCampusId(user.getCampusId());
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
