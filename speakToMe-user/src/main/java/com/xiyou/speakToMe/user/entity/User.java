package com.xiyou.speakToMe.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表实体。
 * 敏感字段（openidCipher/phoneCipher）为密文存储，查询一律走 hash 索引。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** openid AES-256-GCM 密文 */
    private String openidCipher;

    /** openid SHA-256 摘要（唯一索引，用于查询） */
    private String openidHash;

    /** unionid 密文（预留） */
    private String unionidCipher;

    private String nickname;

    private String avatarUrl;

    /** 所属校区，首次登录必选 */
    private Long campusId;

    /** 手机号密文（二期启用） */
    private String phoneCipher;

    /** 手机号摘要 */
    private String phoneHash;

    /** 0 普通用户 1 管理员 */
    private Integer role;

    /** 1 正常 0 封禁 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
