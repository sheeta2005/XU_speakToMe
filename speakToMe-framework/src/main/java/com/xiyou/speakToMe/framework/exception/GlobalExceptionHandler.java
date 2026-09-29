package com.xiyou.speakToMe.framework.exception;

import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.framework.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器：所有异常统一转为 Result 结构，避免泄漏堆栈与内部信息。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：直接返回其错误码与提示 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e);
    }

    /** 请求体缺失 / JSON 解析失败 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ErrorCode.BAD_PARAM, "请求参数格式错误");
    }

    /** 路径/查询参数类型不匹配 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ErrorCode.BAD_PARAM, "参数类型错误: " + e.getName());
    }

    /** 兜底异常：记录完整堆栈，但对外仅返回通用提示 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后再试");
    }
}
