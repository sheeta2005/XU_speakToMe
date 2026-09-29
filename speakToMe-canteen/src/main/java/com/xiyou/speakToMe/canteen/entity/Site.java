package com.xiyou.speakToMe.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 美食地点实体（校内食堂 / 校外区域，如"东区校外""雁塔校外"）。
 */
@Data
@TableName("site")
public class Site {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long campusId;

    private String name;

    /** 1 校内食堂 2 校外区域 3 其他 */
    private Integer siteType;

    private String location;

    private String openTime;

    private String coverUrl;

    /** 平均分（冗余，写入时聚合更新） */
    private BigDecimal avgRating;

    private Integer ratingCount;

    private Integer sort;

    /** 1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
