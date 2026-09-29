package com.xiyou.speakToMe.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容审核日志实体（保留 >=60 天，每日归档清理）。
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;

    private Long userId;

    /** 送审内容快照 */
    private String content;

    /** 1 本地敏感词 2 微信 msgSecCheck */
    private Integer checkType;

    private String hitWord;

    /** 0 正常 1 嫌疑 2 违规 */
    private Integer riskLevel;

    private Integer wxErrCode;

    private String wxErrMsg;

    /** 1 放行 0 拦截 */
    private Integer result;

    private LocalDateTime createAt;
}
