package com.xiyou.speakToMe.config;

import org.springframework.core.env.Environment;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Base64;

/** 10/20：启动即校验必填配置，避免带着无效凭证运行到首个请求才报错。 */
@Component
public class RuntimeConfiguration implements BeanFactoryPostProcessor, EnvironmentAware {
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // 10/20：在创建数据库连接、词库和微信客户端前给出配置错误。
        String[] profiles = environment.getActiveProfiles();
        boolean demo = Arrays.asList(profiles).contains("demo");
        if (demo && profiles.length != 1) {
            throw new IllegalStateException("demo 必须单独启用，禁止与 prod、local 或其他环境混用");
        }
        String key = environment.getProperty("security.aes.key", "");
        try {
            if (Base64.getDecoder().decode(key).length != 32) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("AES_KEY 必须为 32 字节随机值的 Base64，初始化后请稳定保存");
        }
        if (!demo) {
            require(environment, "wx.miniapp.appid", "WX_APPID");
            require(environment, "wx.miniapp.secret", "WX_SECRET");
            require(environment, "spring.datasource.password", "MYSQL_PASSWORD");
        }
    }

    private static void require(Environment environment, String property, String variable) {
        if (environment.getProperty(property, "").isBlank()) {
            throw new IllegalStateException("缺少 " + variable + " 配置，请参照 docs/快速开始.md");
        }
    }
}
