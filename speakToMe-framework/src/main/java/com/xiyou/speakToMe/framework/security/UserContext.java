package com.xiyou.speakToMe.framework.security;

/**
 * 当前登录用户上下文（ThreadLocal）。
 * 由 LoginInterceptor 在请求进入时写入，请求结束自动清理；
 * 业务代码通过 UserContext.getUserId() 获取当前用户，禁止直接信任前端参数。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String token) {
        USER_ID.set(userId);
        TOKEN.set(token);
    }

    /** 当前登录用户 ID，未登录返回 null */
    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getToken() {
        return TOKEN.get();
    }

    public static boolean isLogin() {
        return USER_ID.get() != null;
    }

    public static void clear() {
        USER_ID.remove();
        TOKEN.remove();
    }
}
