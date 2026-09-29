package com.xiyou.speakToMe.framework.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web 工具：客户端 IP 提取（穿透反向代理取 X-Forwarded-For 首个真实 IP）。
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
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !UNKNOWN.equalsIgnoreCase(ip)) {
            // 取第一个（真实客户端）
            int idx = ip.indexOf(',');
            return (idx > 0 ? ip.substring(0, idx) : ip).trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !UNKNOWN.equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
