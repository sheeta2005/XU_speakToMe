package com.xiyou.speakToMe.framework.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import lombok.Data;

/**
 * 全局统一响应体：{ "code": 0, "msg": "ok", "data": ... }
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = ErrorCode.SUCCESS;
        r.msg = ErrorCode.SUCCESS_MSG;
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }

    public static <T> Result<T> fail(BizException e) {
        return fail(e.getCode(), e.getMessage());
    }
}
