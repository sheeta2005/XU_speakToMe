package com.xiyou.speakToMe.ops;

import com.xiyou.speakToMe.config.DatabaseCommand;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 10/22：独立真实 MySQL 验证空库、存量接管、校验失败与非破坏升级。 */
@EnabledIfEnvironmentVariable(named = "TEST_MYSQL_PORT", matches = "\\d+")
class MigrationIntegrationTest {
    private JdbcTemplate root;
    private JdbcTemplate jdbc;
    private String database;
    private String url;
    private String user;
    private String password;

    @BeforeEach
    void setUp() {
        String host = System.getenv().getOrDefault("TEST_MYSQL_HOST", "127.0.0.1");
        String base = "jdbc:mysql://" + host + ":" + System.getenv("TEST_MYSQL_PORT")
                + "/?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai";
        user = System.getenv().getOrDefault("TEST_MYSQL_USER", "root");
        password = System.getenv("TEST_MYSQL_PASSWORD");
        root = new JdbcTemplate(new DriverManagerDataSource(base, user, password));
        database = "test_migration_" + UUID.randomUUID().toString().replace("-", "");
        root.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        url = base.replace("/?", "/" + database + "?");
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    }

    @AfterEach
    void tearDown() {
        // 10/22：数据库名由本用例生成，绝不删除操作者配置的现有数据库。
        if (database != null && database.matches("test_migration_[a-f0-9]{32}")) {
            root.execute("DROP DATABASE " + database);
        }
    }

    private Flyway flyway() {
        return Flyway.configure().dataSource(url, user, password).cleanDisabled(true).load();
    }

    @Test
    void freshDatabaseMigratesOnceWithoutDemoDataAndAllowsLocalBlockLog() {
        assertEquals(2, flyway().migrate().migrationsExecuted);
        assertEquals(0, flyway().migrate().migrationsExecuted);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM campus", Integer.class));
        jdbc.update("INSERT INTO audit_log(review_id,user_id,content,check_type,result) VALUES(NULL,1,'本地拦截',1,0)");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class));
    }

    @Test
    void nonemptyUnversionedDatabaseIsRefusedUntilExplicitVerifiedBaseline() throws Exception {
        Flyway.configure().dataSource(url, user, password).target("1").load().migrate();
        jdbc.execute("DROP TABLE flyway_schema_history");
        jdbc.update("INSERT INTO campus(name) VALUES('保留的校区')");
        assertThrows(org.flywaydb.core.api.FlywayException.class, () -> flyway().migrate());
        DatabaseCommand.baseline(url, user, password);
        flyway().migrate();
        assertEquals("保留的校区", jdbc.queryForObject("SELECT name FROM campus", String.class));
        assertEquals("2", flyway().info().current().getVersion().toString());
    }

    @Test
    void incompatibleLegacySchemaCannotBeBaselined() {
        Flyway.configure().dataSource(url, user, password).target("1").load().migrate();
        jdbc.execute("DROP TABLE flyway_schema_history");
        jdbc.execute("ALTER TABLE campus MODIFY name VARCHAR(16) NOT NULL");
        assertThrows(IllegalStateException.class, () -> DatabaseCommand.baseline(url, user, password));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='flyway_schema_history'",
                Integer.class, database));
    }

    @Test
    void changedMigrationChecksumFailsValidationWithoutRepairingHistory() {
        flyway().migrate();
        jdbc.update("UPDATE flyway_schema_history SET checksum=1 WHERE version='1'");
        assertThrows(org.flywaydb.core.api.FlywayException.class, () -> flyway().migrate());
        assertEquals(1, jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='1'", Integer.class));
    }

    @Test
    void demoSeedsAreIsolatedAndDoNotOverwriteEditedDataOnRestart() {
        Flyway demo = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration", "classpath:db/demo").load();
        demo.migrate();
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM campus", Integer.class));
        jdbc.update("UPDATE campus SET name='操作者维护的校区' WHERE id=1");
        demo.migrate();
        assertEquals("操作者维护的校区", jdbc.queryForObject("SELECT name FROM campus WHERE id=1", String.class));
    }
}
