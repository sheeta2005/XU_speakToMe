package com.xiyou.speakToMe.user.client;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.user.config.WxProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信开放接口客户端。
 * 一期使用 code2session（登录）；内容安全 msgSecCheck 在 content 模块复用同一网关。
 */
@Slf4j
@Component
@Profile("!demo") // 10/20：真实微信客户端不参与本地演示环境。
public class WechatClient {

    private final WxProperties wxProperties;

    public WechatClient(WxProperties wxProperties) {
        this.wxProperties = wxProperties;
    }

    /**
     * code2session：wx.login 的 code 换 openid。
     * 返回 openid；调用失败抛业务异常（前端提示重新登录）。
     */
    public String code2Session(String code) {
        Map<String, Object> params = new HashMap<>();
        params.put("appid", wxProperties.getAppid());
        params.put("secret", wxProperties.getSecret());
        params.put("js_code", code);
        params.put("grant_type", "authorization_code");

        // 10/21：传输与解析失败统一转业务异常，日志不输出凭证、openid 或原始响应。
        try {
            String body = HttpUtil.get(wxProperties.getSessionUrl(), params, 5000);
            JSONObject json = JSONUtil.parseObj(body);

            if (json.containsKey("errcode") && !json.getStr("errcode", "").matches("-?\\d+")) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "微信登录响应异常，请稍后重试");
            }
            Integer errcode = json.getInt("errcode");
            if (errcode != null && errcode != 0) {
                log.warn("code2session 失败 errcode={}", errcode);
                throw new BizException(ErrorCode.SYSTEM_ERROR, "微信登录失败，请稍后重试");
            }
            String openid = json.getStr("openid");
            if (StrUtil.isBlank(openid)) {
                log.warn("code2session 返回无 openid");
                throw new BizException(ErrorCode.SYSTEM_ERROR, "微信登录失败，请重新进入小程序");
            }
            return openid;
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("code2session 请求或解析异常 type={}", e.getClass().getSimpleName());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信登录失败，请稍后重试");
        }
    }
}
