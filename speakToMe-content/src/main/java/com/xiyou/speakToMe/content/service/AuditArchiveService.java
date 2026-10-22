package com.xiyou.speakToMe.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/** 10/22：锁定一批过期日志，先持久化并校验文件，再在同一数据库事务中删除原记录。 */
@Slf4j
@Service
public class AuditArchiveService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    private final Path directory;
    private final int retentionDays;
    private final int batchSize;
    private final boolean enabled;

    public AuditArchiveService(JdbcTemplate jdbc, TransactionTemplate transaction, ObjectMapper json,
            @Value("${audit.archive.directory:./var/audit-archive}") String directory,
            @Value("${audit.archive.retention-days:60}") int retentionDays,
            @Value("${audit.archive.batch-size:500}") int batchSize,
            @Value("${audit.archive.enabled:true}") boolean enabled) {
        if (retentionDays < 60 || batchSize < 1 || batchSize > 5000) {
            throw new IllegalArgumentException("审核日志至少保留 60 天，每批必须为 1—5000 条");
        }
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.json = json;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${audit.archive.cron:0 0 2 * * ?}", zone = "Asia/Shanghai")
    public void scheduledArchive() {
        if (!enabled) return;
        // 10/22：每次最多处理十批，避免长事务和无限占用调度线程；积压由健康检查报告。
        int total = 0;
        for (int batch = 0; batch < 10; batch++) {
            int count = archiveBatch();
            total += count;
            if (count < batchSize) break;
        }
        log.info("审核日志归档完成 rows={}", total);
    }

    public int archiveBatch() {
        Integer count = transaction.execute(status -> {
            // 10/22：跳过其他实例持有的行锁，不依赖单机锁或 Redis，防止多实例重复删除。
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM audit_log WHERE create_at < ? ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED",
                    Timestamp.valueOf(LocalDateTime.now().minusDays(retentionDays)), batchSize);
            if (rows.isEmpty()) return 0;
            try {
                Files.createDirectories(directory);
                Files.setPosixFilePermissions(directory, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
                Path archive = directory.resolve("audit-" + UUID.randomUUID() + ".jsonl");
                byte[] bytes = serialize(rows);
                durableWrite(archive, bytes);
                String checksum = checksum(bytes);
                if (!checksum.equals(checksum(Files.readAllBytes(archive)))) {
                    throw new IllegalStateException("审核归档校验失败，保留原始日志");
                }
                durableWrite(Path.of(archive + ".sha256"), checksum.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                // 10/22：文件系统支持时同步目录项；不支持时失败并保留数据库记录。
                try (FileChannel folder = FileChannel.open(directory, StandardOpenOption.READ)) {
                    folder.force(true);
                }
                List<Object> ids = rows.stream().map(row -> {
                    Object id = row.get("id");
                    return id == null ? row.get("ID") : id;
                }).toList();
                String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
                int deleted = jdbc.update("DELETE FROM audit_log WHERE id IN (" + placeholders + ")", ids.toArray());
                if (deleted != rows.size()) throw new IllegalStateException("归档删除数量不一致，回滚本批");
                return deleted;
            } catch (Exception exception) {
                // 10/22：文件已写但事务失败时可能重复归档，恢复时按原始 id 去重；绝不先删数据库。
                throw new IllegalStateException("审核日志归档失败，原记录未删除", exception);
            }
        });
        return Objects.requireNonNull(count);
    }

    private byte[] serialize(List<Map<String, Object>> rows) throws Exception {
        StringBuilder lines = new StringBuilder();
        for (Map<String, Object> row : rows) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            row.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT),
                    value instanceof Timestamp time ? time.toLocalDateTime().toString() : value));
            lines.append(json.writeValueAsString(normalized)).append('\n');
        }
        return lines.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void durableWrite(Path path, byte[] bytes) throws Exception {
        try (FileChannel channel = FileChannel.open(path,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static String checksum(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
