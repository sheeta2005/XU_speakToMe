package com.xiyou.speakToMe.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiyou.speakToMe.content.entity.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReportMapper extends BaseMapper<Report> {
    /** 10/16：在内容模块内校验公开评论并累计举报，避免反向依赖美食模块。 */
    @Update("UPDATE review SET report_count = report_count + 1 WHERE id = #{id} "
            + "AND module = 'food' AND target_type = 'dish' AND audit_status = 1 AND is_deleted = 0")
    int incrementReportCount(@Param("id") Long id);
}
