package com.xiyou.speakToMe.canteen.controller;

import com.xiyou.speakToMe.canteen.dto.ReviewCreateReq;
import com.xiyou.speakToMe.canteen.dto.ReviewVO;
import com.xiyou.speakToMe.canteen.service.ReviewService;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.framework.ratelimit.RateLimit;
import com.xiyou.speakToMe.framework.result.Result;
import com.xiyou.speakToMe.framework.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点评接口（一期核心）：提交评论（限流 5 条/分钟/用户）、删除自己的评论。
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 提交点评：本地敏感词 + 微信 msgSecCheck 双检，审核通过后即时可见 */
    @PostMapping
    @RateLimit(key = "rate:comment:{userId}", limit = 5, window = 60)
    public Result<ReviewVO> submit(@RequestBody ReviewCreateReq req) {
        return Result.ok(reviewService.submit(requireUserId(), req));
    }

    /** 删除自己的评论（逻辑删除 + 评分回滚） */
    @DeleteMapping("/{reviewId}")
    public Result<Void> delete(@PathVariable Long reviewId) {
        reviewService.delete(requireUserId(), reviewId);
        return Result.ok();
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
