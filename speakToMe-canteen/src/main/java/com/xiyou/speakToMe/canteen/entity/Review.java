package com.xiyou.speakToMe.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 点评评论实体（通用点评表，module/target_type 支持二期交易、三期课程复用）。
 */
@Data
@TableName("review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务模块：food / trade / course */
    private String module;

    /** 目标类型：dish / stall / site / item / course */
    private String targetType;

    private Long targetId;

    private Long userId;

    /** 1-5 星 */
    private Integer rating;

    private String content;

    /** 图片列表（二期预留） */
    private String images;

    /** 0 待审核 1 通过 2 拦截 3 人工复审 */
    private Integer auditStatus;

    private String auditResult;

    private Integer reportCount;

    /** 逻辑删除：0 正常 1 已删除 */
    private Integer isDeleted;

    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
