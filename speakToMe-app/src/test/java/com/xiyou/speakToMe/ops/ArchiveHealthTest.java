package com.xiyou.speakToMe.ops;

import com.xiyou.speakToMe.config.AuditArchiveHealthIndicator;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 10/22：积压与存储异常能够被监控发现，响应不包含数据库或路径细节。 */
class ArchiveHealthTest {
    @TempDir Path directory;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE audit_log(id BIGINT PRIMARY KEY, create_at TIMESTAMP)");
    }

    @Test
    void emptyQueueAndWritableStorageAreHealthy() {
        assertEquals(Status.UP, new AuditArchiveHealthIndicator(jdbc, directory.toString(), true, 60).health().getStatus());
    }

    @Test
    void archiveBacklogOlderThanGracePeriodIsUnhealthyWithoutDetails() {
        jdbc.update("INSERT INTO audit_log VALUES(1, ?)", LocalDateTime.now().minusDays(62));
        var health = new AuditArchiveHealthIndicator(jdbc, directory.toString(), true, 60).health();
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().isEmpty());
    }

    @Test
    void unusableDirectoryAndDatabaseFailureAreUnhealthy() throws Exception {
        Path file = directory.resolve("不可写目录");
        Files.writeString(file, "文件");
        assertEquals(Status.DOWN, new AuditArchiveHealthIndicator(jdbc, file.toString(), true, 60).health().getStatus());
        jdbc.execute("DROP TABLE audit_log");
        assertEquals(Status.DOWN, new AuditArchiveHealthIndicator(jdbc, directory.toString(), true, 60).health().getStatus());
    }

    @Test
    void disabledDemoArchiveDoesNotNeedDirectoryOrDatabase() {
        assertEquals(Status.UP, new AuditArchiveHealthIndicator(null, directory.toString(), false, 60).health().getStatus());
    }
}
