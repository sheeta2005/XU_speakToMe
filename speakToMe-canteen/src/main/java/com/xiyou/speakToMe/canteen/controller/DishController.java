package com.xiyou.speakToMe.canteen.controller;

import com.xiyou.speakToMe.canteen.dto.DishVO;
import com.xiyou.speakToMe.canteen.dto.ReviewVO;
import com.xiyou.speakToMe.canteen.service.DishService;
import com.xiyou.speakToMe.canteen.service.ReviewService;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.framework.result.PageResult;
import com.xiyou.speakToMe.framework.result.Result;
import com.xiyou.speakToMe.framework.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜品接口：档口菜品列表、菜品详情（评分分布）、评论分页。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DishController {

    private final DishService dishService;
    private final ReviewService reviewService;

    /** 某档口/店铺下的在售菜品 */
    @GetMapping("/stalls/{stallId}/dishes")
    public Result<List<DishVO>> listByStall(@PathVariable Long stallId) {
        return Result.ok(dishService.listByStall(stallId));
    }

    /** 菜品详情 + 评分统计 */
    @GetMapping("/dishes/{dishId}")
    public Result<DishVO> detail(@PathVariable Long dishId) {
        return Result.ok(dishService.detail(dishId));
    }

    /** 菜品评论分页（sort=latest/high/low） */
    @GetMapping("/dishes/{dishId}/reviews")
    public Result<PageResult<ReviewVO>> reviews(@PathVariable Long dishId,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(defaultValue = "latest") String sort) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BizException(ErrorCode.BAD_PARAM, "分页参数错误");
        }
        return Result.ok(reviewService.listByDish(dishId, UserContext.getUserId(), page, size, sort));
    }
}
