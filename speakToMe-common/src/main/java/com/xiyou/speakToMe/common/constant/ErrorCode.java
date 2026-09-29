package com.xiyou.speakToMe.common.constant;

/**
 * 全局统一错误码。
 * 约定：0 成功；1xxxx 鉴权与权限；2xxxx 业务参数/业务规则；3xxxx 限流风控；5xxxx 系统异常。
 */
public final class ErrorCode {

    public static final int SUCCESS = 0;
    public static final String SUCCESS_MSG = "ok";

    /** 未登录 */
    public static final int UNAUTHORIZED = 10001;
    /** 登录态过期 */
    public static final int SESSION_EXPIRED = 10002;
    /** 无权限（非管理员等） */
    public static final int FORBIDDEN = 10003;

    /** 参数错误 */
    public static final int BAD_PARAM = 20001;
    /** 评论字数不符合（10-200） */
    public static final int CONTENT_LENGTH_INVALID = 20002;
    /** 内容包含违规信息（命中违禁词） */
    public static final int CONTENT_BLOCKED = 20003;
    /** 目标不存在或已删除 */
    public static final int TARGET_NOT_FOUND = 20004;
    /** 无权操作该资源 */
    public static final int NO_PERMISSION_OP = 20005;

    /** 操作过于频繁（限流） */
    public static final int TOO_MANY_REQUESTS = 30001;

    /** 系统繁忙 */
    public static final int SYSTEM_ERROR = 50001;

    private ErrorCode() {
    }
}
