package com.xiyou.speakToMe.content.controller;

import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.framework.ratelimit.RateLimit;
import com.xiyou.speakToMe.framework.result.Result;
import com.xiyou.speakToMe.framework.security.UserContext;
import com.xiyou.speakToMe.content.service.ReportService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报接口：POST /api/v1/reports（需登录）。
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Data
    public static class ReportReq {
        private Long reviewId;
        private Integer reasonType;
        private String reasonDetail;
    }

    /** 举报违规评论（限流：单用户每分钟 5 次） */
    @PostMapping
    @RateLimit(key = "rate:report:{userId}", limit = 5, window = 60)
    public Result<Long> submit(@RequestBody ReportReq req) {
        Long userId = requireUserId();
        return Result.ok(reportService.submit(
                req.getReviewId(), userId, req.getReasonType(), req.getReasonDetail()));
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
