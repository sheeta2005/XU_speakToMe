package com.xiyou.speakToMe.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置：AppID/AppSecret 经环境变量注入，禁止入库入日志入仓库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wx.miniapp")
public class WxProperties {

    /** 小程序 AppID */
    private String appid;

    /** 小程序 AppSecret */
    private String secret;

    /** code2session 接口地址 */
    private String sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";

    /** 文本内容安全接口地址 */
    private String msgSecCheckUrl = "https://api.weixin.qq.com/wxa/msg_sec_check";
}
