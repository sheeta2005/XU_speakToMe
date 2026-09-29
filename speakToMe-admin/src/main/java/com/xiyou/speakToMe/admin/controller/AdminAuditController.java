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
import com.xiyou.speakToMe.framework.result.PageResult;
import com.xiyou.speakToMe.framework.result.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人工复审（管理端）：微信返回 risky / 本地命中复审类词的评论进入此队列，
 * 管理员 24 小时内确认通过或拦截。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminGuard adminGuard;
    private final ReviewMapper reviewMapper;
    private final DishService dishService;

    @Data
    public static class ConfirmReq {
        /** pass 通过 / block 拦截 */
        private String action;
    }

    /** 复审队列（auditStatus=3） */
    @GetMapping("/reviews")
    public Result<PageResult<Review>> queue(@RequestParam(defaultValue = "3") Integer auditStatus,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size) {
        adminGuard.requireAdmin();
        Page<Review> result = reviewMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getAuditStatus, auditStatus)
                        .eq(Review::getIsDeleted, 0)
                        .orderByAsc(Review::getCreatedAt));
        return Result.ok(PageResult.of(result));
    }

    /** 复审确认 */
    @PostMapping("/audit/{reviewId}/confirm")
    public Result<Void> confirm(@PathVariable Long reviewId, @RequestBody ConfirmReq req) {
        adminGuard.requireAdmin();
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BizException(ErrorCode.TARGET_NOT_FOUND, "评论不存在");
        }
        if (!"pass".equals(req.getAction()) && !"block".equals(req.getAction())) {
            throw new BizException(ErrorCode.BAD_PARAM, "action 需为 pass 或 block");
        }
        boolean pass = "pass".equals(req.getAction());
        reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                .eq(Review::getId, reviewId)
                .set(Review::getAuditStatus, pass ? 1 : 2)
                .set(Review::getAuditResult, pass ? "人工复审通过" : "人工复审拦截"));
        if (pass && "dish".equals(review.getTargetType())) {
            dishService.refreshRating(review.getTargetId());
        }
        return Result.ok();
    }
}
