package com.xiyou.speakToMe.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyou.speakToMe.content.service.AuditArchiveService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 10/22：真实文件与数据库事务验证先归档后删除，任何归档失败都保留原日志。 */
@org.junit.jupiter.api.condition.EnabledOnOs({org.junit.jupiter.api.condition.OS.LINUX, org.junit.jupiter.api.condition.OS.MAC})
class AuditArchiveTest {
    @TempDir Path directory;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE audit_log(id BIGINT PRIMARY KEY, content VARCHAR(500), create_at TIMESTAMP)");
        jdbc.update("INSERT INTO audit_log VALUES(1, '历史中文内容', ?), (2, '应当保留的内容', ?)",
                LocalDateTime.now().minusDays(61), LocalDateTime.now().minusDays(59));
    }

    private AuditArchiveService service(Path path, int retention, int batch) {
        return new AuditArchiveService(jdbc, transaction, new ObjectMapper().findAndRegisterModules(),
                path.toString(), retention, batch, true);
    }

    @Test
    void expiredRowsAreArchivedWithChecksumBeforeRemovalAndRecentRowsStay() throws Exception {
        assertEquals(1, service(directory, 60, 100).archiveBatch());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class));
        try (var files = Files.list(directory)) {
            Path archive = files.filter(p -> p.toString().endsWith(".jsonl")).findFirst().orElseThrow();
            String data = Files.readString(archive);
            assertTrue(data.contains("历史中文内容"));
            assertFalse(data.contains("应当保留的内容"));
            assertTrue(Files.exists(Path.of(archive + ".sha256")));
            String checksum = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(archive)));
            assertEquals(checksum, Files.readString(Path.of(archive + ".sha256")).trim());
        }
        assertEquals(0, service(directory, 60, 100).archiveBatch());
    }

    @Test
    void unwritableArchiveLocationCannotDeleteSourceRows() throws Exception {
        Path file = directory.resolve("不能作为目录的文件");
        Files.writeString(file, "保留");
        assertThrows(RuntimeException.class, () -> service(file, 60, 100).archiveBatch());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class));
    }

    @Test
    void deletionFailureRollsBackWhileKeepingDurableArchive() throws Exception {
        jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT protect_old CHECK (id > 0)");
        jdbc.execute("CREATE TABLE reference_log(id BIGINT REFERENCES audit_log(id))");
        jdbc.update("INSERT INTO reference_log VALUES(1)");
        assertThrows(RuntimeException.class, () -> service(directory, 60, 100).archiveBatch());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.filter(p -> p.toString().endsWith(".jsonl")).count());
        }
    }

    @Test
    void batchSizeBoundsWorkAndRetentionCannotBeReducedBelowSixtyDays() {
        jdbc.update("INSERT INTO audit_log VALUES(3, '第二条历史记录', ?)", LocalDateTime.now().minusDays(90));
        assertEquals(1, service(directory, 60, 1).archiveBatch());
        assertEquals(1, service(directory, 60, 1).archiveBatch());
        assertEquals(0, service(directory, 60, 1).archiveBatch());
        assertThrows(IllegalArgumentException.class, () -> service(directory, 59, 100));
        assertThrows(IllegalArgumentException.class, () -> service(directory, 60, 0));
        assertThrows(IllegalArgumentException.class, () -> service(directory, 60, 5001));
    }
}
