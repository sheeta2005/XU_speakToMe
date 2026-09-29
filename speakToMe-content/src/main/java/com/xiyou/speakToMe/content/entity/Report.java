package com.xiyou.speakToMe.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报表实体。
 */
@Data
@TableName("report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;

    private Long reporterId;

    /** 1垃圾广告 2色情 3辱骂 4政治敏感 5学术不端 6其他 */
    private Integer reasonType;

    private String reasonDetail;

    /** 0 待处理 1 已处理 2 驳回 */
    private Integer status;

    private String handleResult;

    private Long handlerId;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
