package com.xiyou.speakToMe.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 校区实体（对象可新增，管理端维护）。
 */
@Data
@TableName("campus")
public class Campus {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer sort;

    /** 1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
