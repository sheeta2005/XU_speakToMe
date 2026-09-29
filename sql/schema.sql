-- =========================================================
-- 西邮点评家 数据库初始化脚本（可直接执行，可重复执行）
-- 建库：CREATE DATABASE IF NOT EXISTS speak_to_me DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 执行：mysql -uroot -p speak_to_me < schema.sql
-- =========================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------
-- 1. 校区表（对象可新增：管理端可维护）
-- ---------------------------------------------------------
DROP TABLE IF EXISTS `campus`;
CREATE TABLE `campus` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`       VARCHAR(32)  NOT NULL COMMENT '校区名称：长安校区/雁塔校区',
  `sort`       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序',
  `status`     TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校区表';

-- ---------------------------------------------------------
-- 2. 用户表（openid 密文+摘要双列存储）
-- ---------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid_cipher`   VARCHAR(512) NOT NULL COMMENT 'openid AES-256-GCM 密文',
  `openid_hash`     CHAR(64)     NOT NULL COMMENT 'openid SHA-256 摘要，用于唯一查询',
  `unionid_cipher`  VARCHAR(512) DEFAULT NULL COMMENT 'unionid 密文（预留）',
  `nickname`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
  `avatar_url`      VARCHAR(512) NOT NULL DEFAULT '' COMMENT '头像 URL',
  `campus_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '校区，首次登录必选',
  `phone_cipher`    VARCHAR(512) DEFAULT NULL COMMENT '手机号密文（二期启用）',
  `phone_hash`      CHAR(64)     DEFAULT NULL COMMENT '手机号摘要',
  `role`            TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0普通用户 1管理员',
  `status`          TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1正常 0封禁',
  `last_login_at`   DATETIME DEFAULT NULL COMMENT '最近登录时间',
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid_hash` (`openid_hash`),
  KEY `idx_campus` (`campus_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ---------------------------------------------------------
-- 3. 美食地点表 site（对象不限于食堂：校内食堂 / 校外区域，如"东区校外""雁塔校外"）
-- ---------------------------------------------------------
DROP TABLE IF EXISTS `site`;
CREATE TABLE `site` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `campus_id`    BIGINT UNSIGNED NOT NULL COMMENT '所属校区',
  `name`         VARCHAR(64)  NOT NULL COMMENT '地点名：旭日苑 / 东区校外 / 雁塔校外',
  `site_type`    TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1校内食堂 2校外区域 3其他',
  `location`     VARCHAR(255) NOT NULL DEFAULT '' COMMENT '位置描述',
  `open_time`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '营业时间文本',
  `cover_url`    VARCHAR(512) NOT NULL DEFAULT '' COMMENT '封面图（预留）',
  `avg_rating`   DECIMAL(2,1) NOT NULL DEFAULT 0.0 COMMENT '平均分（冗余）',
  `rating_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评分人数（冗余）',
  `sort`         TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `status`       TINYINT UNSIGNED NOT NULL DEFAULT 1,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_campus` (`campus_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='美食地点表（校内食堂/校外区域）';

-- ---------------------------------------------------------
-- 4. 档口/店铺表（校外区域下的店铺也走本表）
-- ---------------------------------------------------------
DROP TABLE IF EXISTS `stall`;
CREATE TABLE `stall` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `site_id`     BIGINT UNSIGNED NOT NULL COMMENT '所属美食地点',
  `name`        VARCHAR(64)  NOT NULL COMMENT '档口/店铺名，如一楼重庆小面',
  `floor`       VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '楼层/区域描述',
  `category`    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '品类：面食/川菜/饮品',
  `sort`        TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_site` (`site_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='档口/店铺表（校外区域可为店铺）';

-- ---------------------------------------------------------
-- 5. 菜品表
-- ---------------------------------------------------------
DROP TABLE IF EXISTS `dish`;
CREATE TABLE `dish` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `stall_id`     BIGINT UNSIGNED NOT NULL,
  `name`         VARCHAR(64)  NOT NULL,
  `description`  VARCHAR(255) NOT NULL DEFAULT '',
  `price`        DECIMAL(8,2) DEFAULT NULL COMMENT '价格（可选）',
  `avg_rating`   DECIMAL(2,1) NOT NULL DEFAULT 0.0 COMMENT '平均分（冗余）',
  `rating_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评分人数（冗余）',
  `is_available` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1在售 0下架',
  `sort`         TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `status`       TINYINT UNSIGNED NOT NULL DEFAULT 1,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_stall` (`stall_id`),
  KEY `idx_rating` (`avg_rating`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品表';

-- ---------------------------------------------------------
-- 6. 点评评论表（通用点评表：一期评菜品 module=food，二期评商品 module=trade，
--      三期评课程 module=course，零 DDL 复用）
-- ---------------------------------------------------------
DROP TABLE IF EXISTS `review`;
CREATE TABLE `review` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `module`        VARCHAR(32)  NOT NULL DEFAULT 'food' COMMENT '业务模块：food/trade/course（一期美食点评）',
  `target_type`   VARCHAR(32)  NOT NULL COMMENT '目标类型：dish/stall/site/item/course',
  `target_id`     BIGINT UNSIGNED NOT NULL COMMENT '目标对象 ID',
  `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '点评人',
  `rating`        TINYINT UNSIGNED NOT NULL COMMENT '1-5 星',
  `content`       VARCHAR(500) NOT NULL COMMENT '正文，一期限制 10-200 字',
  `images`        JSON DEFAULT NULL COMMENT '图片列表（二期预留）',
  `audit_status`  TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待审核 1通过 2拦截 3人工复审',
  `audit_result`  VARCHAR(128) DEFAULT NULL COMMENT '审核结果备注',
  `report_count`  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '被举报次数',
  `is_deleted`    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除（用户自删/违规下架）',
  `deleted_at`    DATETIME DEFAULT NULL,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_target` (`module`,`target_type`,`target_id`,`created_at`),
  KEY `idx_user` (`user_id`,`created_at`),
  KEY `idx_audit` (`audit_status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点评评论表（一期菜品点评，后续模块复用）';

-- ---------------------------------------------------------
-- 7. 内容审核日志表（保留 >=60 天，每日归档清理）
-- ---------------------------------------------------------
DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `review_id`    BIGINT UNSIGNED NOT NULL COMMENT '关联评论',
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `content`      VARCHAR(500) NOT NULL COMMENT '送审内容快照',
  `check_type`   TINYINT UNSIGNED NOT NULL COMMENT '1本地敏感词 2微信msgSecCheck',
  `hit_word`     VARCHAR(128) DEFAULT NULL COMMENT '命中的敏感词',
  `risk_level`   TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0正常 1嫌疑 2违规',
  `wx_err_code`  INT DEFAULT NULL COMMENT '微信返回错误码',
  `wx_err_msg`   VARCHAR(128) DEFAULT NULL,
  `result`       TINYINT UNSIGNED NOT NULL COMMENT '1放行 0拦截',
  `create_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_review` (`review_id`),
  KEY `idx_create` (`create_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容审核日志（保留>=60天）';

-- ---------------------------------------------------------
-- 8. 举报表
-- ---------------------------------------------------------
DROP TABLE IF EXISTS `report`;
CREATE TABLE `report` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `review_id`     BIGINT UNSIGNED NOT NULL COMMENT '被举报评论',
  `reporter_id`   BIGINT UNSIGNED NOT NULL COMMENT '举报人',
  `reason_type`   TINYINT UNSIGNED NOT NULL COMMENT '1垃圾广告 2色情 3辱骂 4政治敏感 5学术不端 6其他',
  `reason_detail` VARCHAR(200) NOT NULL DEFAULT '',
  `status`        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待处理 1已处理 2驳回',
  `handle_result` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '处理结果',
  `handler_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '处理人',
  `handled_at`    DATETIME DEFAULT NULL COMMENT '处理时间（承诺24小时内）',
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`,`created_at`),
  KEY `idx_review` (`review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报表';

-- ---------------------------------------------------------
-- 9. 敏感词库表
-- ---------------------------------------------------------
DROP TABLE IF EXISTS `sensitive_word`;
CREATE TABLE `sensitive_word` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `word`       VARCHAR(64) NOT NULL COMMENT '敏感词',
  `category`   TINYINT UNSIGNED NOT NULL COMMENT '1政治 2色情 3辱骂 4广告 5学术不端 6其他',
  `level`      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1直接拦截 2人工复审',
  `status`     TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `source`     VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT '来源：manual/wx-feedback/import',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感词库表';

-- ---------------------------------------------------------
-- 10. 通用内容表 post（二期预留：二手交易/失物招领/拼单跑腿 共用）
-- ---------------------------------------------------------
DROP TABLE IF EXISTS `post`;
CREATE TABLE `post` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `module`        VARCHAR(32) NOT NULL COMMENT 'trade/lost_found/errand',
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `title`         VARCHAR(64)  NOT NULL,
  `content`       TEXT         NOT NULL,
  `price`         DECIMAL(10,2) DEFAULT NULL COMMENT '价格（二手/拼单用）',
  `images`        JSON DEFAULT NULL,
  `status`        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0展示 1下架 2已成交',
  `audit_status`  TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待审核 1通过 2拦截 3人工复审',
  `audit_result`  VARCHAR(128) DEFAULT NULL,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_module` (`module`,`status`,`created_at`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用内容表（二期预留）';

-- ---------------------------------------------------------
-- 11. 交易订单表 trade_order（二期预留：二手交易/拼单跑腿订单）
-- ---------------------------------------------------------
DROP TABLE IF EXISTS `trade_order`;
CREATE TABLE `trade_order` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`   VARCHAR(32) NOT NULL,
  `module`     VARCHAR(32) NOT NULL COMMENT 'trade/errand',
  `post_id`    BIGINT UNSIGNED DEFAULT NULL,
  `buyer_id`   BIGINT UNSIGNED NOT NULL,
  `seller_id`  BIGINT UNSIGNED NOT NULL,
  `amount`     DECIMAL(10,2) DEFAULT NULL,
  `status`     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0进行中 1完成 2取消 3投诉',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易订单表（二期预留）';

-- =========================================================
-- 种子数据（可直接修改，运营通过管理端维护）
-- =========================================================

-- 校区
INSERT INTO `campus` (`id`, `name`, `sort`) VALUES
(1, '长安校区', 1),
(2, '雁塔校区', 2);

-- 美食地点（校内食堂 / 校外区域）
INSERT INTO `site` (`id`, `campus_id`, `name`, `site_type`, `location`, `open_time`) VALUES
(1, 1, '旭日苑',        1, '长安校区东侧', '6:30-21:00'),
(2, 1, '美食广场',      1, '长安校区中心', '7:00-22:00'),
(3, 1, '东区校外',      2, '东区校门外商业街', '10:00-23:00'),
(4, 2, '学生食堂',      1, '雁塔校区内',   '6:30-20:30'),
(5, 2, '雁塔校外',      2, '雁塔校区南门外', '10:00-23:00');

-- 档口/店铺
INSERT INTO `stall` (`id`, `site_id`, `name`, `floor`, `category`) VALUES
(1, 1, '一楼重庆小面', '1F', '面食'),
(2, 1, '二楼川菜窗口', '2F', '川菜'),
(3, 3, '老字号肉夹馍', '',  '快餐'),
(4, 4, '一楼早餐窗口', '1F', '早餐');

-- 菜品
INSERT INTO `dish` (`id`, `stall_id`, `name`, `description`, `price`) VALUES
(1, 1, '红烧牛肉面', '筋道面条配大块牛肉', 12.00),
(2, 1, '重庆豌杂面', '豌豆软糯，杂酱鲜香', 10.00),
(3, 2, '宫保鸡丁',   '经典川味，微辣回甜', 9.00),
(4, 3, '腊汁肉夹馍', '肥瘦相间，外酥里嫩', 8.00),
(5, 4, '豆腐脑',     '咸甜可选', 3.50);

SET FOREIGN_KEY_CHECKS = 1;
