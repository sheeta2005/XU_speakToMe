package com.xiyou.speakToMe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyou.speakToMe.content.service.AuditArchiveService;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/** 10/22：容器内一次性数据库操作，不启动 Web、演示账号或任何业务定时任务。 */
public final class DatabaseCommand {
    private DatabaseCommand() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("必须指定 migrate、baseline 或 archive");
        String url = "jdbc:mysql://" + env("MYSQL_HOST", "mysql") + ":" + env("MYSQL_PORT", "3306")
                + "/speak_to_me?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai";
        String user = env("MYSQL_USER", "speak_to_me");
        String password = System.getenv("MYSQL_PASSWORD");
        switch (args[0]) {
            case "baseline" -> baseline(url, user, password);
            case "migrate" -> Flyway.configure().dataSource(url, user, password).cleanDisabled(true).load().migrate();
            case "archive" -> {
                var ds = new DriverManagerDataSource(url, user, password);
                var service = new AuditArchiveService(new JdbcTemplate(ds),
                        new TransactionTemplate(new DataSourceTransactionManager(ds)),
                        new ObjectMapper().findAndRegisterModules(), env("AUDIT_ARCHIVE_DIRECTORY", "/app/archive"),
                        Integer.parseInt(env("AUDIT_RETENTION_DAYS", "60")), 500, true);
                System.out.println("本批归档条数：" + service.archiveBatch());
            }
            default -> throw new IllegalArgumentException("未知数据库操作");
        }
    }

    public static void baseline(String url, String user, String password) throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        String actual = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (actual == null || actual.isBlank()) throw new IllegalStateException("必须明确目标数据库");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='flyway_schema_history'",
                Integer.class, actual) != 0) throw new IllegalStateException("数据库已存在版本历史，禁止重新基线化");
        // 10/22：临时参考库只含 V1 结构。完整比较表、字段、索引后才接管，不能盲目 baselineOnMigrate。
        String reference = "baseline_reference_" + UUID.randomUUID().toString().replace("-", "");
        int databaseStart = url.indexOf('/', "jdbc:mysql://".length()) + 1;
        int queryStart = url.indexOf('?', databaseStart);
        String referenceUrl = url.substring(0, databaseStart) + reference
                + (queryStart < 0 ? "" : url.substring(queryStart));
        jdbc.execute("CREATE DATABASE " + reference + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        try {
            Flyway.configure().dataSource(referenceUrl, user, password).target("1").load().migrate();
            if (!schemaShape(jdbc, actual).equals(schemaShape(jdbc, reference))) {
                throw new IllegalStateException("存量库结构与 V1 不一致，禁止接管；请先人工核对差异");
            }
            Flyway.configure().dataSource(url, user, password).baselineVersion("1")
                    .baselineDescription("10/22：结构核验后的存量数据库").load().baseline();
        } finally {
            // 10/22：仅删除本方法生成的随机参考库，从不对目标库执行 DROP 或 clean。
            jdbc.execute("DROP DATABASE " + reference);
        }
    }

    private static List<Map<String, Object>> schemaShape(JdbcTemplate jdbc, String schema) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(jdbc.queryForList("""
                SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.tables
                WHERE table_schema=? AND table_name<>'flyway_schema_history' ORDER BY TABLE_NAME
                """, schema));
        result.addAll(jdbc.queryForList("""
                SELECT TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,
                       EXTRA,CHARACTER_SET_NAME,COLLATION_NAME,GENERATION_EXPRESSION
                FROM information_schema.columns WHERE table_schema=? AND table_name<>'flyway_schema_history'
                ORDER BY TABLE_NAME,ORDINAL_POSITION
                """, schema));
        result.addAll(jdbc.queryForList("""
                SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART,INDEX_TYPE,EXPRESSION
                FROM information_schema.statistics WHERE table_schema=? AND table_name<>'flyway_schema_history'
                ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX
                """, schema));
        result.addAll(jdbc.queryForList("""
                SELECT TABLE_NAME,CONSTRAINT_NAME,CONSTRAINT_TYPE
                FROM information_schema.table_constraints WHERE table_schema=? AND table_name<>'flyway_schema_history'
                ORDER BY TABLE_NAME,CONSTRAINT_NAME
                """, schema));
        return result;
    }

    private static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }
}
