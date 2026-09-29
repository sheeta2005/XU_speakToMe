package com.xiyou.speakToMe.framework.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.framework.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器：校验 Authorization: Bearer <token>，
 * 通过则写入 UserContext，未通过直接返回 10001/10002，不进入业务代码。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    public LoginInterceptor(SessionService sessionService, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String token = resolveToken(request);
        if (token == null) {
            writeUnauthorized(response, ErrorCode.UNAUTHORIZED, "未登录");
            return false;
        }
        Long userId = sessionService.verify(token);
        if (userId == null) {
            writeUnauthorized(response, ErrorCode.SESSION_EXPIRED, "登录态已过期，请重新登录");
            return false;
        }
        UserContext.set(userId, token);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith(BEARER_PREFIX)) {
            return auth.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, msg)));
    }
}
