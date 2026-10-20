package com.xiyou.speakToMe.config;

import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.content.client.WechatContentClient;
import com.xiyou.speakToMe.user.client.WechatClient;
import com.xiyou.speakToMe.user.config.WxProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 10/20：仅独立 demo 环境提供固定演示身份；数据库、会话和权限仍走真实实现。 */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
public class DemoClientsConfiguration {
    @Bean
    WechatClient demoWechatClient(RuntimeConfiguration checkedConfiguration) {
        return new WechatClient(new WxProperties()) {
            @Override
            public String code2Session(String code) {
                return switch (code) {
                    case "demo-user" -> "demo-user";
                    case "demo-admin" -> "demo-admin";
                    default -> throw new BizException(ErrorCode.BAD_PARAM, "请使用演示登录入口");
                };
            }
        };
    }

    @Bean
    WechatContentClient demoContentClient(RuntimeConfiguration checkedConfiguration) {
        return new WechatContentClient(null) {
            @Override
            public CheckResult checkText(String content, String openid, String clientMsgId) {
                // 10/20：演示评论全部转人工复审，不能误认为通过真实微信内容安全检查。
                return new CheckResult(false, true, false, 0, "review");
            }
        };
    }
}
