package com.xiyou.speakToMe.content.client;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信内容安全客户端：security.msgSecCheck（v2，文本审核）。
 *
 * 说明：v2 接口要求 scene=2（评论）、openid、clientMsgId 参数；
 * access_token 通过 cgi-bin/token 获取并缓存到 Redis（微信有效期 7200s，保守取 7000s）。
 */
@Slf4j
@Component
@Profile("!demo") // 10/20：正式环境始终使用真实微信内容安全客户端。
public class WechatContentClient {

    private static final String TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential";
    private static final String TOKEN_CACHE_KEY = "wx:access_token";
    private static final String CONTENT_CHECK_URL = "https://api.weixin.qq.com/wxa/msg_sec_check";

    @Value("${wx.miniapp.appid:}")
    private String appid;

    @Value("${wx.miniapp.secret:}")
    private String secret;

    @Value("${wx.miniapp.msg-sec-check-url:" + CONTENT_CHECK_URL + "}")
    private String msgSecCheckUrl;

    private final StringRedisTemplate redis;

    public WechatContentClient(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 审核结果：pass 合规 / review 需人工 / risky 违规 */
    public record CheckResult(boolean pass, boolean review, boolean risky, int label, String suggest) {
    }

    /**
     * 文本内容安全检测。
     *
     * @param content    送审文本
     * @param openid     用户 openid（scene=2 必填）
     * @param clientMsgId 业务消息 ID（评论 ID），便于微信端去重追溯
     */
    public CheckResult checkText(String content, String openid, String clientMsgId) {
        // 10/21：审核缺失、超时及解析异常必须进入调用方人工复审，不能默认放行。
        try {
            String token = accessToken();
            JSONObject body = new JSONObject();
            body.set("version", 2);
            body.set("scene", 2);
            body.set("openid", openid);
            body.set("content", content);
            body.set("clientMsgId", clientMsgId);

            String resp = HttpUtil.post(msgSecCheckUrl + "?access_token=" + token, body.toString(), 5000);
            JSONObject json = JSONUtil.parseObj(resp);

            if (json.containsKey("errcode") && !json.getStr("errcode", "").matches("-?\\d+")) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "内容安全服务响应异常");
            }
            Integer errcode = json.getInt("errcode");
            if (errcode != null && errcode != 0) {
                log.warn("msgSecCheck 调用失败 errcode={}", errcode);
                // 10/21：失效凭证不继续缓存到七千秒，下一条点评重新获取；本条仍转人工。
                if (errcode == 40001 || errcode == 40014 || errcode == 42001) {
                    redis.delete(TOKEN_CACHE_KEY);
                }
                // 87014 表示内容违规（老语义）；其余按接口异常降级处理由调用方决定
                if (errcode == 87014) {
                    return new CheckResult(false, false, true, 0, "risky");
                }
                throw new BizException(ErrorCode.SYSTEM_ERROR, "内容安全服务暂不可用");
            }
            JSONObject result = json.getJSONObject("result");
            if (result == null) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "内容安全服务缺少审核结果");
            }
            String suggest = result.getStr("suggest", "");
            int label = result.getInt("label", 0);
            return switch (suggest) {
                case "pass" -> new CheckResult(true, false, false, label, suggest);
                case "review" -> new CheckResult(false, true, false, label, suggest);
                case "risky" -> new CheckResult(false, false, true, label, suggest);
                default -> throw new BizException(ErrorCode.SYSTEM_ERROR, "内容安全服务审核结果异常");
            };
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("msgSecCheck 请求、缓存或解析异常 type={}", e.getClass().getSimpleName());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "内容安全服务暂不可用");
        }
    }

    /** 获取 access_token（Redis 缓存，TTL 7000s） */
    private String accessToken() {
        String cached = redis.opsForValue().get(TOKEN_CACHE_KEY);
        if (StrUtil.isNotBlank(cached)) {
            return cached;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("appid", appid);
        params.put("secret", secret);
        String resp = HttpUtil.get(TOKEN_URL, params, 5000);
        JSONObject json = JSONUtil.parseObj(resp);
        String token = json.getStr("access_token");
        if (StrUtil.isBlank(token)) {
            // 10/21：原始响应可能含凭证，只记录稳定错误码。
            log.error("获取 access_token 失败 errcode={}", json.getInt("errcode"));
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信凭证获取失败");
        }
        redis.opsForValue().set(TOKEN_CACHE_KEY, token, Duration.ofSeconds(7000));
        return token;
    }
}
