package com.xiyou.speakToMe.canteen.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiyou.speakToMe.canteen.dto.ReviewCreateReq;
import com.xiyou.speakToMe.canteen.dto.ReviewVO;
import com.xiyou.speakToMe.canteen.entity.Dish;
import com.xiyou.speakToMe.canteen.entity.Review;
import com.xiyou.speakToMe.canteen.mapper.DishMapper;
import com.xiyou.speakToMe.canteen.mapper.ReviewMapper;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.common.util.AesGcmUtil;
import com.xiyou.speakToMe.content.client.WechatContentClient;
import com.xiyou.speakToMe.content.filter.SensitiveWordFilter;
import com.xiyou.speakToMe.content.service.AuditLogService;
import com.xiyou.speakToMe.framework.result.PageResult;
import com.xiyou.speakToMe.user.entity.User;
import com.xiyou.speakToMe.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 点评核心服务。
 *
 * 提交链路（严格顺序）：
 *   1. 参数校验（rating 1-5、正文 10-200 字、目标菜品存在且在售）   [Controller 层已做限流]
 *   2. 本地敏感词 DFA 预检：命中 level=1 直接拦截（记日志，不调微信）
 *   3. 微信 security.msgSecCheck v2：pass 放行 / review 人工复审 / risky 拦截
 *      微信接口异常降级：转人工复审，不阻塞正常用户
 *   4. 入库 review + 审核日志（保留 >=60 天）
 *   5. 审核通过后刷新评分聚合（dish 冗余字段 + 缓存失效）
 */
@Slf4j
@Service
public class ReviewService {

    @Value("${security.aes.key:}")
    private String aesKey;

    @Value("${business.comment.content-min:10}")
    private int contentMin;

    @Value("${business.comment.content-max:200}")
    private int contentMax;

    private final ReviewMapper reviewMapper;
    private final DishMapper dishMapper;
    private final UserMapper userMapper;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final WechatContentClient wechatContentClient;
    private final AuditLogService auditLogService;
    private final DishService dishService;

    public ReviewService(ReviewMapper reviewMapper, DishMapper dishMapper, UserMapper userMapper,
                         SensitiveWordFilter sensitiveWordFilter, WechatContentClient wechatContentClient,
                         AuditLogService auditLogService, DishService dishService) {
        this.reviewMapper = reviewMapper;
        this.dishMapper = dishMapper;
        this.userMapper = userMapper;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.wechatContentClient = wechatContentClient;
        this.auditLogService = auditLogService;
        this.dishService = dishService;
    }

    /**
     * 提交点评（Controller 层已挂 @RateLimit 用户维度限流 5 条/分钟；
     * IP 维度限流由 WAF/Nginx 层保障）。
     */
    @Transactional
    public ReviewVO submit(Long userId, ReviewCreateReq req) {
        // 1) 基础校验
        if (req.getRating() == null || req.getRating() < 1 || req.getRating() > 5) {
            throw new BizException(ErrorCode.BAD_PARAM, "评分需为 1-5 星");
        }
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (content.length() < contentMin || content.length() > contentMax) {
            throw new BizException(ErrorCode.CONTENT_LENGTH_INVALID,
                    "评论字数需为 " + contentMin + "-" + contentMax + " 字");
        }
        if ("dish".equals(req.getTargetType())) {
            Dish dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                    .eq(Dish::getId, req.getTargetId())
                    .eq(Dish::getStatus, 1));
            if (dish == null || dish.getIsAvailable() == null || dish.getIsAvailable() == 0) {
                throw new BizException(ErrorCode.TARGET_NOT_FOUND, "菜品不存在或已下架");
            }
        } else {
            throw new BizException(ErrorCode.BAD_PARAM, "一期仅支持菜品点评");
        }

        // 2) 本地敏感词预检
        SensitiveWordFilter.MatchResult local = sensitiveWordFilter.match(content);
        if (local.hasLevel1()) {
            auditLogService.log(null, userId, content, 1, local.firstHitWord(), 2, 0);
            log.warn("本地敏感词拦截 userId={} word={}", userId, local.firstHitWord());
            throw new BizException(ErrorCode.CONTENT_BLOCKED, "内容包含违规信息，请修改后再提交");
        }
        boolean needManualReview = local.hasLevel2();

        // 3) 先落库占位（拿 reviewId 作为微信 clientMsgId）
        Review review = new Review();
        review.setModule(StrUtil.isBlank(req.getModule()) ? "food" : req.getModule());
        review.setTargetType(req.getTargetType());
        review.setTargetId(req.getTargetId());
        review.setUserId(userId);
        review.setRating(req.getRating());
        review.setContent(content);
        review.setAuditStatus(0);
        review.setReportCount(0);
        review.setIsDeleted(0);
        reviewMapper.insert(review);

        // 4) 微信内容安全审核（scene=2 需真实 openid）
        int auditStatus;
        try {
            String openid = resolveOpenid(userId);
            WechatContentClient.CheckResult wx =
                    wechatContentClient.checkText(content, openid, String.valueOf(review.getId()));
            if (wx.pass()) {
                auditStatus = 1;
            } else if (wx.review()) {
                auditStatus = 3;
            } else {
                auditStatus = 2;
            }
            if (needManualReview && auditStatus == 1) {
                // 本地命中复审类词但微信放行：仍进人工复审，从严处理
                auditStatus = 3;
            }
            auditLogService.logWx(review.getId(), userId, content,
                    wx.risky() ? 2 : (auditStatus == 3 ? 1 : 0),
                    null, null, auditStatus == 2 ? 0 : 1);
        } catch (BizException e) {
            // 微信服务不可用：降级为人工复审，不阻塞用户
            auditStatus = 3;
            auditLogService.logWx(review.getId(), userId, content, 1,
                    null, "微信审核服务异常，降级人工复审", 1);
            log.warn("msgSecCheck 降级 userId={} reviewId={}", userId, review.getId());
        }

        // 5) 回写审核状态
        review.setAuditStatus(auditStatus);
        review.setAuditResult(auditStatus == 3 ? "人工复审" : null);
        reviewMapper.updateById(review);

        if (auditStatus == 2) {
            throw new BizException(ErrorCode.CONTENT_BLOCKED, "内容需修改后重新提交");
        }
        if (auditStatus == 1) {
            dishService.refreshRating(req.getTargetId());
        }
        return ReviewVO.from(review, null, null, true);
    }

    /** 菜品评论分页列表（仅展示审核通过 + 本人可见的复审中评论） */
    public PageResult<ReviewVO> listByDish(Long dishId, Long currentUserId, long page, long size, String sort) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BizException(ErrorCode.BAD_PARAM, "分页参数错误");
        }
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getModule, "food")
                .eq(Review::getTargetType, "dish")
                .eq(Review::getTargetId, dishId)
                .eq(Review::getIsDeleted, 0)
                .in(Review::getAuditStatus, 1, 3);
        if ("high".equals(sort)) {
            wrapper.orderByDesc(Review::getRating).orderByDesc(Review::getCreatedAt);
        } else if ("low".equals(sort)) {
            wrapper.orderByAsc(Review::getRating).orderByDesc(Review::getCreatedAt);
        } else {
            wrapper.orderByDesc(Review::getCreatedAt);
        }
        Page<Review> result = reviewMapper.selectPage(new Page<>(page, size), wrapper);

        // 组装用户信息
        List<Long> userIds = result.getRecords().stream().map(Review::getUserId).distinct().toList();
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        List<ReviewVO> vos = result.getRecords().stream()
                .map(r -> {
                    User u = userMap.get(r.getUserId());
                    String nickname = u == null ? "西邮同学" : (StrUtil.isBlank(u.getNickname()) ? "西邮同学" : u.getNickname());
                    String avatar = u == null ? "" : u.getAvatarUrl();
                    return ReviewVO.from(r, nickname, avatar,
                            currentUserId != null && currentUserId.equals(r.getUserId()));
                })
                .toList();
        return PageResult.of(vos, result.getTotal(), page, size);
    }

    /** 删除自己的评论：逻辑删除 + 评分回滚 */
    @Transactional
    public void delete(Long userId, Long reviewId) {
        Review review = reviewMapper.selectOne(new LambdaQueryWrapper<Review>()
                .eq(Review::getId, reviewId)
                .eq(Review::getIsDeleted, 0));
        if (review == null) {
            throw new BizException(ErrorCode.TARGET_NOT_FOUND, "评论不存在或已删除");
        }
        if (!review.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NO_PERMISSION_OP, "只能删除自己发布的评论");
        }
        reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                .eq(Review::getId, reviewId)
                .set(Review::getIsDeleted, 1)
                .set(Review::getDeletedAt, LocalDateTime.now()));
        // 重算评分（聚合口径自动排除已删除评论）
        if (review.getAuditStatus() == 1) {
            dishService.refreshRating(review.getTargetId());
        }
    }

    /** 解密用户 openid（msgSecCheck scene=2 必填） */
    private String resolveOpenid(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || StrUtil.isBlank(user.getOpenidCipher())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "用户凭证缺失");
        }
        return AesGcmUtil.decrypt(user.getOpenidCipher(), aesKey);
    }
}
