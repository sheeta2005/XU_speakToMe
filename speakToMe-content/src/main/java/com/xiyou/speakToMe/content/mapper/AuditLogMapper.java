package com.xiyou.speakToMe.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiyou.speakToMe.content.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
