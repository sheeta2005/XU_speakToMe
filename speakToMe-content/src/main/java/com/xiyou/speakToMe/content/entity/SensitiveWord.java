package com.xiyou.speakToMe.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词库实体。
 * category：1政治 2色情 3辱骂 4广告 5学术不端 6其他
 * level：1直接拦截 2人工复审
 */
@Data
@TableName("sensitive_word")
public class SensitiveWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String word;

    private Integer category;

    private Integer level;

    /** 1启用 0停用 */
    private Integer status;

    private String source;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
