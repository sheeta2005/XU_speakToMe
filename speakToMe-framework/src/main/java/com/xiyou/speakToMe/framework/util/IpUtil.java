package com.xiyou.speakToMe.framework.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web 工具：读取容器认定的客户端地址。
 */
public final class IpUtil {

    private static final String UNKNOWN = "unknown";

    private IpUtil() {
    }

    public static String getClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return UNKNOWN;
        }
        return getClientIp(attrs.getRequest());
    }

    public static String getClientIp(HttpServletRequest request) {
        // 10/21：直连客户端可伪造代理头；受信代理应由容器与部署网络统一配置。
        return request.getRemoteAddr();
    }
}
