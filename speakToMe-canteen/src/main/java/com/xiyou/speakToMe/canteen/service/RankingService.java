package com.xiyou.speakToMe.canteen.service;

import com.xiyou.speakToMe.canteen.dto.HotDishVO;
import com.xiyou.speakToMe.canteen.entity.Dish;
import com.xiyou.speakToMe.canteen.mapper.DishMapper;
import com.xiyou.speakToMe.canteen.mapper.DishMapper.HotDishRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 综合排行榜服务（近期最火菜品）。
 *
 * 实现：定时任务每 10 分钟聚合近 7 天 / 30 天榜单写入 Redis ZSET
 * （rank:hot:dish:{days}，member=dishId，score=hotScore），
 * 接口直接读缓存零延迟；停售/下架菜品由聚合 SQL 天然剔除。
 */
@Slf4j
@Service
public class RankingService {

    private static final String ZSET_PREFIX = "rank:hot:dish:";
    private static final int[] DAYS = {7, 30};

    private final DishMapper dishMapper;
    private final StringRedisTemplate redis;

    public RankingService(DishMapper dishMapper, StringRedisTemplate redis) {
        this.dishMapper = dishMapper;
        this.redis = redis;
    }

    /** 读取榜单（优先缓存，缓存为空时实时计算兜底） */
    public List<HotDishVO> hotDishes(int days, int limit) {
        if (days != 7 && days != 30) {
            days = 7;
        }
        if (limit < 1 || limit > 50) {
            limit = 20;
        }
        String key = ZSET_PREFIX + days;

        Set<String> members = redis.opsForZSet().reverseRange(key, 0, limit - 1);
        List<HotDishVO> result = new ArrayList<>();
        if (members == null || members.isEmpty()) {
            // 缓存未命中（如服务重启后定时任务未跑）：实时聚合兜底
            List<HotDishRow> rows = dishMapper.selectHotDishes(days);
            int idx = 1;
            for (HotDishRow row : rows) {
                if (idx > limit) {
                    break;
                }
                HotDishVO vo = new HotDishVO();
                vo.setRank(idx);
                vo.setDishId(row.dishId());
                vo.setDishName(row.dishName());
                vo.setSiteName(row.siteName());
                vo.setStallName(row.stallName());
                vo.setAvgRating(row.avgRating());
                vo.setRecentReviewCount(row.recentReviewCount());
                vo.setHotScore(row.hotScore());
                result.add(vo);
                idx++;
            }
            return result;
        }

        // 缓存命中：按排名取菜品信息组装
        Map<Long, HotDishRow> rowMap = new HashMap<>();
        dishMapper.selectHotDishes(days).forEach(row -> rowMap.put(row.dishId(), row));
        int rank = 1;
        for (String member : members) {
            Long dishId = Long.valueOf(member);
            HotDishRow row = rowMap.get(dishId);
            if (row == null) {
                continue;
            }
            HotDishVO vo = new HotDishVO();
            vo.setRank(rank);
            vo.setDishId(dishId);
            vo.setDishName(row.dishName());
            vo.setSiteName(row.siteName());
            vo.setStallName(row.stallName());
            vo.setAvgRating(row.avgRating());
            vo.setRecentReviewCount(row.recentReviewCount());
            Double score = redis.opsForZSet().score(key, member);
            vo.setHotScore(score == null ? row.hotScore() : BigDecimal.valueOf(score));
            result.add(vo);
            rank++;
        }
        return result;
    }

    /** 定时重算榜单（每 10 分钟一次，7 天与 30 天窗口） */
    @Scheduled(cron = "0 */10 * * * ?")
    public void rebuild() {
        for (int days : DAYS) {
            String key = ZSET_PREFIX + days;
            List<HotDishRow> rows = dishMapper.selectHotDishes(days);
            redis.delete(key);
            for (HotDishRow row : rows) {
                // 时间衰减加权：近 3 天评论额外 1.5 倍（SQL 已含基础热度，此处不做二次加权，
                // 如需更强时效性可在 SQL 中按 created_at 分桶加权）
                redis.opsForZSet().add(key, String.valueOf(row.dishId()), row.hotScore().doubleValue());
            }
            log.info("排行榜重算完成 days={} size={}", days, rows.size());
        }
    }
}
