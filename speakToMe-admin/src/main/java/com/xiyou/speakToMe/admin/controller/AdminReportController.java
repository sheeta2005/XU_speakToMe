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
import org.springframework.transaction.annotation.Transactional;

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
        // 10/16：后台查询同样限制分页和状态，避免无效状态及无限制取数。
        if (page < 1 || size < 1 || size > 50 || status == null || status < 0 || status > 2) {
            throw new BizException(ErrorCode.BAD_PARAM, "分页或工单状态错误");
        }
        Page<Report> result = reportMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getStatus, status)
                        .orderByAsc(Report::getCreatedAt, Report::getId));
        return Result.ok(PageResult.of(result));
    }

    /** 处理举报工单 */
    @PostMapping("/{id}/handle")
    @Transactional
    public Result<Void> handle(@PathVariable Long id, @RequestBody HandleReq req) {
        adminGuard.requireAdmin();
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BizException(ErrorCode.TARGET_NOT_FOUND, "工单不存在");
        }
        if (!"block".equals(req.getAction()) && !"dismiss".equals(req.getAction())) {
            throw new BizException(ErrorCode.BAD_PARAM, "action 需为 block 或 dismiss");
        }
        // 10/16：已处理工单不可覆盖；条件更新防止多人同时处理同一工单。
        if (!Integer.valueOf(0).equals(report.getStatus())) {
            throw new BizException(ErrorCode.BAD_PARAM, "工单已处理，请刷新列表");
        }
        String result = req.getHandleResult() == null ? "" : req.getHandleResult().trim();
        if (result.length() > 200) {
            throw new BizException(ErrorCode.BAD_PARAM, "处理说明不能超过 200 字");
        }
        boolean blocked = "block".equals(req.getAction());
        // 更新工单
        report.setStatus(blocked ? 1 : 2);
        report.setHandleResult(result);
        report.setHandlerId(UserContext.getUserId());
        report.setHandledAt(LocalDateTime.now());
        if (reportMapper.update(report, new LambdaUpdateWrapper<Report>()
                .eq(Report::getId, id).eq(Report::getStatus, 0)) != 1) {
            throw new BizException(ErrorCode.BAD_PARAM, "工单已处理，请刷新列表");
        }

        if (blocked) {
            // 下架被举报评论（audit_status=2 对所有用户不可见）
            // 10/16：锁定评论，串行执行同一条评论的审核及评分刷新。
            Review review = reviewMapper.selectOne(new LambdaQueryWrapper<Review>()
                    .eq(Review::getId, report.getReviewId()).last("FOR UPDATE"));
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
