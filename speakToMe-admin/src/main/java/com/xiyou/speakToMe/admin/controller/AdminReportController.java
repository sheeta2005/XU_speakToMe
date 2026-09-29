package com.xiyou.speakToMe.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiyou.speakToMe.admin.security.AdminGuard;
import com.xiyou.speakToMe.canteen.entity.Review;
import com.xiyou.speakToMe.canteen.mapper.ReviewMapper;
import com.xiyou.speakToMe.canteen.service.DishService;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.content.entity.Report;
import com.xiyou.speakToMe.content.mapper.ReportMapper;
import com.xiyou.speakToMe.framework.result.PageResult;
import com.xiyou.speakToMe.framework.result.Result;
import com.xiyou.speakToMe.framework.security.UserContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 举报处理（管理端）：工单列表 + 处理（block 下架违规评论 / dismiss 驳回），
 * 承诺 24 小时内处理。
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminGuard adminGuard;
    private final ReportMapper reportMapper;
    private final ReviewMapper reviewMapper;
    private final DishService dishService;

    @Data
    public static class HandleReq {
        /** block 下架 / dismiss 驳回 */
        private String action;
        private String handleResult;
    }

    /** 举报工单分页（status=0 待处理） */
    @GetMapping
    public Result<PageResult<Report>> list(@RequestParam(defaultValue = "0") Integer status,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        adminGuard.requireAdmin();
        Page<Report> result = reportMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getStatus, status)
                        .orderByAsc(Report::getCreatedAt));
        return Result.ok(PageResult.of(result));
    }

    /** 处理举报工单 */
    @PostMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody HandleReq req) {
        adminGuard.requireAdmin();
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BizException(ErrorCode.TARGET_NOT_FOUND, "工单不存在");
        }
        if (!"block".equals(req.getAction()) && !"dismiss".equals(req.getAction())) {
            throw new BizException(ErrorCode.BAD_PARAM, "action 需为 block 或 dismiss");
        }
        boolean blocked = "block".equals(req.getAction());
        // 更新工单
        report.setStatus(1);
        report.setHandleResult(req.getHandleResult() == null ? "" : req.getHandleResult());
        report.setHandlerId(UserContext.getUserId());
        report.setHandledAt(LocalDateTime.now());
        reportMapper.updateById(report);

        if (blocked) {
            // 下架被举报评论（audit_status=2 对所有用户不可见）
            Review review = reviewMapper.selectById(report.getReviewId());
            if (review != null && (review.getIsDeleted() == null || review.getIsDeleted() == 0)) {
                reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                        .eq(Review::getId, review.getId())
                        .set(Review::getAuditStatus, 2)
                        .set(Review::getAuditResult, "举报核实违规，已下架"));
                if ("dish".equals(review.getTargetType())
                        && (review.getAuditStatus() == null || review.getAuditStatus() == 1)) {
                    dishService.refreshRating(review.getTargetId());
                }
            }
        }
        return Result.ok();
    }
}
