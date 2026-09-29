package com.xiyou.speakToMe.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiyou.speakToMe.content.entity.AuditLog;
import com.xiyou.speakToMe.content.mapper.AuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 内容审核日志服务：写日志 + 每日归档（保留 >=60 天）。
 * 生产环境归档建议：导出至 COS 冷存后再删除，此处提供定时清理骨架。
 */
@Slf4j
@Service
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /** 记录一条审核日志 */
    public void log(Long reviewId, Long userId, String content, int checkType,
                    String hitWord, int riskLevel, int result) {
        AuditLog auditLog = new AuditLog();
        auditLog.setReviewId(reviewId);
        auditLog.setUserId(userId);
        auditLog.setContent(content);
        auditLog.setCheckType(checkType);
        auditLog.setHitWord(hitWord);
        auditLog.setRiskLevel(riskLevel);
        auditLog.setResult(result);
        auditLogMapper.insert(auditLog);
    }

    /** 记录微信审核日志（含错误码） */
    public void logWx(Long reviewId, Long userId, String content, int riskLevel,
                      Integer wxErrCode, String wxErrMsg, int result) {
        AuditLog auditLog = new AuditLog();
        auditLog.setReviewId(reviewId);
        auditLog.setUserId(userId);
        auditLog.setContent(content);
        auditLog.setCheckType(2);
        auditLog.setRiskLevel(riskLevel);
        auditLog.setWxErrCode(wxErrCode);
        auditLog.setWxErrMsg(wxErrMsg);
        auditLog.setResult(result);
        auditLogMapper.insert(auditLog);
    }

    /** 每日凌晨 2 点：清理超过 60 天的审核日志（生产环境改为先归档再删除） */
    @Scheduled(cron = "0 0 2 * * ?")
    public void archiveExpiredLogs() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(60);
        int deleted = auditLogMapper.delete(
                new LambdaQueryWrapper<AuditLog>()
                        .lt(AuditLog::getCreateAt, deadline));
        log.info("审核日志归档清理 {} 条（保留 60 天）", deleted);
    }
}
