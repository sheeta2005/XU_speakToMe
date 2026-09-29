package com.xiyou.speakToMe.common.exception;

import com.xiyou.speakToMe.common.constant.ErrorCode;
import lombok.Getter;

/**
 * 业务异常：由全局异常处理器统一转为 Result 响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        this(ErrorCode.BAD_PARAM, message);
    }
}
