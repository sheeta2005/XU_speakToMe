package com.xiyou.speakToMe.canteen.dto;

import com.xiyou.speakToMe.canteen.entity.Dish;
import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 菜品视图（列表项 + 详情评分统计）。
 */
@Data
public class DishVO {

    private Long id;

    private Long stallId;

    private String name;

    private String description;

    private BigDecimal price;

    private BigDecimal avgRating;

    private Integer ratingCount;

    /** 评分分布（1-5 星各档人数），详情接口返回 */
    private Map<Integer, Integer> distribution;

    public static DishVO from(Dish dish) {
        DishVO vo = new DishVO();
        vo.setId(dish.getId());
        vo.setStallId(dish.getStallId());
        vo.setName(dish.getName());
        vo.setDescription(dish.getDescription());
        vo.setPrice(dish.getPrice());
        vo.setAvgRating(dish.getAvgRating());
        vo.setRatingCount(dish.getRatingCount());
        return vo;
    }

    /** 构造空的 1-5 星分布（缺失档补 0） */
    public static Map<Integer, Integer> emptyDistribution() {
        Map<Integer, Integer> dist = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            dist.put(i, 0);
        }
        return dist;
    }
}
