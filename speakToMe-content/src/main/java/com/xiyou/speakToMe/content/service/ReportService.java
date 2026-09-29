package com.xiyou.speakToMe.content.service;

import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.content.entity.Report;
import com.xiyou.speakToMe.content.mapper.ReportMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 举报服务：提交举报（同一用户对同一评论 24 小时去重）。
 * 被举报评论的审核下架/驳回处理在 admin 模块完成。
 */
@Service
public class ReportService {

    private static final String DUP_PREFIX = "report:dup:";
    private static final Duration DUP_TTL = Duration.ofHours(24);

    private final ReportMapper reportMapper;
    private final StringRedisTemplate redis;

    public ReportService(ReportMapper reportMapper, StringRedisTemplate redis) {
        this.reportMapper = reportMapper;
        this.redis = redis;
    }

    /** 提交举报 */
    public Long submit(Long reviewId, Long reporterId, Integer reasonType, String reasonDetail) {
        if (reviewId == null || reviewId <= 0) {
            throw new BizException(ErrorCode.BAD_PARAM, "举报对象错误");
        }
        if (reasonType == null || reasonType < 1 || reasonType > 6) {
            throw new BizException(ErrorCode.BAD_PARAM, "举报类型错误");
        }
        // 去重：同一举报人对同一评论 24 小时内只能举报一次
        String dupKey = DUP_PREFIX + reporterId + ":" + reviewId;
        Boolean first = redis.opsForValue().setIfAbsent(dupKey, "1", DUP_TTL);
        if (first != null && !first) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "您已举报过该评论");
        }

        Report report = new Report();
        report.setReviewId(reviewId);
        report.setReporterId(reporterId);
        report.setReasonType(reasonType);
        report.setReasonDetail(reasonDetail == null ? "" : reasonDetail);
        report.setStatus(0);
        reportMapper.insert(report);
        return report.getId();
    }
}
