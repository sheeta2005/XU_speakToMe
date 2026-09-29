package com.xiyou.speakToMe.canteen.controller;

import com.xiyou.speakToMe.canteen.dto.HotDishVO;
import com.xiyou.speakToMe.canteen.service.RankingService;
import com.xiyou.speakToMe.framework.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 综合排行榜接口：近期最火菜品（7 天 / 30 天）。
 */
@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping("/hot-dishes")
    public Result<List<HotDishVO>> hotDishes(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(rankingService.hotDishes(days, limit));
    }
}
