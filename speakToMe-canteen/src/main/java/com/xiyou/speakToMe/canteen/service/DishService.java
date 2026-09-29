package com.xiyou.speakToMe.canteen.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiyou.speakToMe.canteen.dto.DishVO;
import com.xiyou.speakToMe.canteen.entity.Dish;
import com.xiyou.speakToMe.canteen.entity.Review;
import com.xiyou.speakToMe.canteen.mapper.DishMapper;
import com.xiyou.speakToMe.canteen.mapper.ReviewMapper;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜品服务：列表、详情（含评分分布）、评分聚合（冗余字段 + Redis 缓存）。
 * 聚合口径：module='food' AND target_type='dish' AND audit_status=1 AND is_deleted=0。
 */
@Slf4j
@Service
public class DishService {

    private static final String STAT_CACHE_PREFIX = "food:dish:";

    private final DishMapper dishMapper;
    private final ReviewMapper reviewMapper;
    private final StringRedisTemplate redis;

    public DishService(DishMapper dishMapper, ReviewMapper reviewMapper, StringRedisTemplate redis) {
        this.dishMapper = dishMapper;
        this.reviewMapper = reviewMapper;
        this.redis = redis;
    }

    /** 某档口/店铺下的在售菜品列表 */
    public List<DishVO> listByStall(Long stallId) {
        return dishMapper.selectList(
                        new LambdaQueryWrapper<Dish>()
                                .eq(Dish::getStallId, stallId)
                                .eq(Dish::getStatus, 1)
                                .eq(Dish::getIsAvailable, 1)
                                .orderByAsc(Dish::getSort))
                .stream().map(DishVO::from).toList();
    }

    /** 菜品详情 + 评分统计（Redis 缓存 30 分钟） */
    public DishVO detail(Long dishId) {
        Dish dish = dishMapper.selectOne(
                new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getId, dishId)
                        .eq(Dish::getStatus, 1));
        if (dish == null) {
            throw new BizException(ErrorCode.TARGET_NOT_FOUND, "菜品不存在或已下架");
        }
        DishVO vo = DishVO.from(dish);
        vo.setDistribution(ratingDistribution(dishId));
        return vo;
    }

    /** 评分分布（缓存命中直接返回） */
    private Map<Integer, Integer> ratingDistribution(Long dishId) {
        String key = STAT_CACHE_PREFIX + dishId + ":stat";
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                JSONObject json = JSONUtil.parseObj(cached);
                Map<Integer, Integer> dist = new LinkedHashMap<>();
                for (int i = 1; i <= 5; i++) {
                    dist.put(i, json.getInt(String.valueOf(i), 0));
                }
                return dist;
            } catch (Exception e) {
                log.warn("评分缓存解析失败，回源计算 dishId={}", dishId);
            }
        }
        Map<Integer, Integer> dist = computeDistribution(dishId);
        // 写入缓存（含均分与人数一并缓存）
        JSONObject json = new JSONObject();
        dist.forEach((k, v) -> json.set(String.valueOf(k), v));
        redis.opsForValue().set(key, json.toString(), Duration.ofMinutes(30));
        return dist;
    }

    /** 从评论表聚合评分分布（有效评论口径） */
    private Map<Integer, Integer> computeDistribution(Long dishId) {
        Map<Integer, Integer> dist = DishVO.emptyDistribution();
        List<Map<String, Object>> rows = reviewMapper.selectMaps(new QueryWrapper<Review>()
                .select("rating", "COUNT(*) AS cnt")
                .eq("module", "food")
                .eq("target_type", "dish")
                .eq("target_id", dishId)
                .eq("audit_status", 1)
                .eq("is_deleted", 0)
                .groupBy("rating"));
        for (Map<String, Object> row : rows) {
            int rating = ((Number) row.get("rating")).intValue();
            int cnt = ((Number) row.get("cnt")).intValue();
            dist.put(rating, cnt);
        }
        return dist;
    }

    /** 评分聚合：重算 avg_rating / rating_count 并写回 dish 表 + 失效缓存（提交/删除评论后调用） */
    public void refreshRating(Long dishId) {
        Map<String, Object> agg = reviewMapper.selectMaps(new QueryWrapper<Review>()
                        .select("AVG(rating) AS avgRating", "COUNT(*) AS cnt")
                        .eq("module", "food")
                        .eq("target_type", "dish")
                        .eq("target_id", dishId)
                        .eq("audit_status", 1)
                        .eq("is_deleted", 0))
                .stream().findFirst().orElse(null);
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            return;
        }
        if (agg == null || agg.get("cnt") == null || ((Number) agg.get("cnt")).longValue() == 0) {
            dish.setAvgRating(BigDecimal.ZERO);
            dish.setRatingCount(0);
        } else {
            dish.setRatingCount(((Number) agg.get("cnt")).intValue());
            dish.setAvgRating(((BigDecimal) agg.get("avgRating")).setScale(1, RoundingMode.HALF_UP));
        }
        dishMapper.updateById(dish);
        // 失效评分缓存，下次读取按需重算
        redis.delete(STAT_CACHE_PREFIX + dishId + ":stat");
    }
}
