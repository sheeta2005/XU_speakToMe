package com.xiyou.speakToMe.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 10/20：验证配置缺失明确失败，演示替身不能进入正式环境。 */
class RuntimeConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RuntimeConfiguration.class)
            .withPropertyValues("security.aes.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "spring.datasource.password=test-password");

    @Test
    void demoNeedsNoWechatCredentials() {
        runner.withPropertyValues("spring.profiles.active=demo")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void demoCannotBeCombinedWithOtherProfiles() {
        for (String profiles : new String[]{"demo,prod", "demo,local", "demo,test"}) {
            runner.withPropertyValues("spring.profiles.active=" + profiles)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void realModeRequiresWechatAndDatabaseCredentials() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("spring.profiles.active=prod", "wx.miniapp.appid=test-app",
                        "wx.miniapp.secret=test-secret")
                .run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("spring.profiles.active=prod", "wx.miniapp.appid=test-app",
                        "wx.miniapp.secret=test-secret", "spring.datasource.password=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void aesMustDecodeToExactly32Bytes() {
        for (String key : new String[]{"", "not-base64!", "dGVzdA=="}) {
            runner.withPropertyValues("spring.profiles.active=demo", "security.aes.key=" + key)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
