package com.xiyou.speakToMe.content.service;

import com.xiyou.speakToMe.content.entity.AuditLog;
import com.xiyou.speakToMe.content.mapper.AuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


/**
 * 内容审核日志写入；10/22：归档与安全清理由 AuditArchiveService 负责。
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

}
