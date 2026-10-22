-- 10/22：本地拦截发生在点评入库之前，允许不关联 review_id；不改动历史业务数据。
ALTER TABLE audit_log MODIFY review_id BIGINT UNSIGNED NULL COMMENT '关联评论；本地预检拦截时为空';
