package com.xiyou.speakToMe.content.service;

import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.content.entity.Report;
import com.xiyou.speakToMe.content.mapper.ReportMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public Long submit(Long reviewId, Long reporterId, Integer reasonType, String reasonDetail) {
        if (reviewId == null || reviewId <= 0) {
            throw new BizException(ErrorCode.BAD_PARAM, "举报对象错误");
        }
        if (reasonType == null || reasonType < 1 || reasonType > 6) {
            throw new BizException(ErrorCode.BAD_PARAM, "举报类型错误");
        }
        // 10/16：先校验说明长度，失败的请求不占用 24 小时举报名额。
        String detail = reasonDetail == null ? "" : reasonDetail.trim();
        if (detail.length() > 200) {
            throw new BizException(ErrorCode.BAD_PARAM, "举报说明不能超过 200 字");
        }
        // 去重：同一举报人对同一评论 24 小时内只能举报一次
        String dupKey = DUP_PREFIX + reporterId + ":" + reviewId;
        Boolean first = redis.opsForValue().setIfAbsent(dupKey, "1", DUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "您已举报过该评论");
        }

        try {
            // 10/16：仅接受未删除且已公开的美食点评，计数与工单在同一事务内写入。
            if (reportMapper.incrementReportCount(reviewId) != 1) {
                throw new BizException(ErrorCode.TARGET_NOT_FOUND, "评论不存在或暂不可举报");
            }
            Report report = new Report();
            report.setReviewId(reviewId);
            report.setReporterId(reporterId);
            report.setReasonType(reasonType);
            report.setReasonDetail(detail);
            report.setStatus(0);
            reportMapper.insert(report);
            return report.getId();
        } catch (RuntimeException ex) {
            // 10/16：数据库写入失败允许用户重试，不保留本次去重标记。
            redis.delete(dupKey);
            throw ex;
        }
    }
}
