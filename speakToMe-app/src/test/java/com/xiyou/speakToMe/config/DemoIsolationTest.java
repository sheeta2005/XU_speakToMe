package com.xiyou.speakToMe.config;

import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.content.client.WechatContentClient;
import com.xiyou.speakToMe.user.client.WechatClient;
import com.xiyou.speakToMe.user.config.WxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 10/20：检查实际 Profile Bean 选择，防止正式客户端被演示替身覆盖。 */
class DemoIsolationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RuntimeConfiguration.class, DemoClientsConfiguration.class,
                    WechatClient.class, WechatContentClient.class, WxProperties.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withPropertyValues("security.aes.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "spring.datasource.password=test-password");

    @Test
    void demoAcceptsOnlyTwoFixedIdentities() {
        runner.withPropertyValues("spring.profiles.active=demo").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(WechatClient.class);
            WechatClient client = context.getBean(WechatClient.class);
            assertThat(client.code2Session("demo-user")).isEqualTo("demo-user");
            assertThat(client.code2Session("demo-admin")).isEqualTo("demo-admin");
            assertThatThrownBy(() -> client.code2Session("arbitrary-code"))
                    .isInstanceOf(BizException.class);
        });
    }

    @Test
    void demoContentAlwaysNeedsManualReview() {
        runner.withPropertyValues("spring.profiles.active=demo").run(context -> {
            assertThat(context).hasSingleBean(WechatContentClient.class);
            var result = context.getBean(WechatContentClient.class).checkText("演示内容", "demo-user", "1");
            assertThat(result.review()).isTrue();
            assertThat(result.pass()).isFalse();
        });
    }

    @Test
    void prodUsesRealClients() {
        runner.withPropertyValues("spring.profiles.active=prod", "wx.miniapp.appid=test-app",
                "wx.miniapp.secret=test-secret").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean("demoWechatClient")
                    .doesNotHaveBean("demoContentClient");
            assertThat(context.getBean(WechatClient.class).getClass()).isEqualTo(WechatClient.class);
            assertThat(context.getBean(WechatContentClient.class).getClass()).isEqualTo(WechatContentClient.class);
        });
    }

    @Test
    void mixedProfileCannotCreateDemoClients() {
        runner.withPropertyValues("spring.profiles.active=demo,prod")
                .run(context -> assertThat(context).hasFailed());
    }
}
