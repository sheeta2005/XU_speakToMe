package com.xiyou.speakToMe.canteen.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 排行榜条目视图（近期最火菜品）。
 */
@Data
public class HotDishVO {

    private Integer rank;

    private Long dishId;

    private String dishName;

    private String siteName;

    private String stallName;

    private BigDecimal avgRating;

    private Integer recentReviewCount;

    private BigDecimal hotScore;
}
