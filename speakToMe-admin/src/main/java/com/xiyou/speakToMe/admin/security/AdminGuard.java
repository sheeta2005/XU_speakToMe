package com.xiyou.speakToMe.admin.security;

import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.framework.security.UserContext;
import com.xiyou.speakToMe.user.entity.User;
import com.xiyou.speakToMe.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

/**
 * 管理端权限守卫：校验当前用户 role=1。
 */
@Component
public class AdminGuard {

    private final UserMapper userMapper;

    public AdminGuard(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public void requireAdmin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        User user = userMapper.selectById(userId);
        // 10/16：封禁账号即使保留管理员角色也不能进行运营操作。
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || !Integer.valueOf(1).equals(user.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无管理权限");
        }
    }
}
