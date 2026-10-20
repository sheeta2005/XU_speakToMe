package com.xiyou.speakToMe.config;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiyou.speakToMe.common.util.AesGcmUtil;
import com.xiyou.speakToMe.user.entity.User;
import com.xiyou.speakToMe.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 10/20：演示账号只在首次启动时创建；重启保留已有资料、校区和权限。 */
@Slf4j
@Component
@Profile("demo")
public class DemoDataInitializer implements ApplicationRunner {
    private final UserMapper users;
    private final String aesKey;

    public DemoDataInitializer(UserMapper users, @Value("${security.aes.key}") String aesKey,
                               RuntimeConfiguration checkedConfiguration) {
        this.users = users;
        this.aesKey = aesKey;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfAbsent("demo-user", "演示同学", 0);
        createIfAbsent("demo-admin", "演示管理员", 1);
        log.warn("当前为本地演示环境：固定身份可登录，微信审核使用人工复审替身；禁止公开部署");
    }

    private void createIfAbsent(String openid, String nickname, int role) {
        String hash = DigestUtil.sha256Hex(openid);
        if (users.selectCount(new LambdaQueryWrapper<User>().eq(User::getOpenidHash, hash)) != 0) {
            return;
        }
        User user = new User();
        user.setOpenidHash(hash);
        user.setOpenidCipher(AesGcmUtil.encrypt(openid, aesKey));
        user.setNickname(nickname);
        user.setAvatarUrl("");
        user.setRole(role);
        user.setStatus(1);
        users.insert(user);
    }
}
