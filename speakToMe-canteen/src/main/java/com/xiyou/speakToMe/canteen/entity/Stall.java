package com.xiyou.speakToMe.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 档口/店铺实体（校外区域下的店铺也走本表）。
 */
@Data
@TableName("stall")
public class Stall {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long siteId;

    private String name;

    private String floor;

    private String category;

    private Integer sort;

    /** 1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
