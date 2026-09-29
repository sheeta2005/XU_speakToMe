package com.xiyou.speakToMe.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜品实体。
 */
@Data
@TableName("dish")
public class Dish {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stallId;

    private String name;

    private String description;

    private BigDecimal price;

    /** 平均分（冗余） */
    private BigDecimal avgRating;

    /** 评分人数（冗余） */
    private Integer ratingCount;

    /** 1 在售 0 下架 */
    private Integer isAvailable;

    private Integer sort;

    /** 1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
