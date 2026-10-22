package com.xiyou.speakToMe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** 10/22：归档目录不可写或积压超过一天即报告失败，避免清理任务长期失败而无人发现。 */
@Component("auditArchiveHealthIndicator")
public class AuditArchiveHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbc;
    private final Path directory;
    private final boolean enabled;
    private final int retentionDays;

    public AuditArchiveHealthIndicator(JdbcTemplate jdbc,
            @Value("${audit.archive.directory:./var/audit-archive}") String directory,
            @Value("${audit.archive.enabled:true}") boolean enabled,
            @Value("${audit.archive.retention-days:60}") int retentionDays) {
        this.jdbc = jdbc;
        this.directory = Path.of(directory);
        this.enabled = enabled;
        this.retentionDays = retentionDays;
    }

    @Override
    public Health health() {
        if (!enabled) return Health.up().build();
        try {
            Files.createDirectories(directory);
            if (!Files.isWritable(directory)) return Health.down().build();
            Integer overdue = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE create_at < ?",
                    Integer.class, Timestamp.valueOf(LocalDateTime.now().minusDays(retentionDays + 1L)));
            return overdue != null && overdue > 0 ? Health.down().build() : Health.up().build();
        } catch (Exception exception) {
            // 10/22：健康响应不包含 SQL、目录、数据库地址或堆栈等内部细节。
            return Health.down().build();
        }
    }
}
