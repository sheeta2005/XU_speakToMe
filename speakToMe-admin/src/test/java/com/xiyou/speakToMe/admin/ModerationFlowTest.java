package com.xiyou.speakToMe.admin;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.xiyou.speakToMe.admin.controller.*;
import com.xiyou.speakToMe.admin.security.AdminGuard;
import com.xiyou.speakToMe.canteen.controller.DishController;
import com.xiyou.speakToMe.canteen.mapper.*;
import com.xiyou.speakToMe.canteen.service.DishService;
import com.xiyou.speakToMe.canteen.service.ReviewService;
import com.xiyou.speakToMe.content.controller.ReportController;
import com.xiyou.speakToMe.content.filter.SensitiveWordFilter;
import com.xiyou.speakToMe.content.mapper.*;
import com.xiyou.speakToMe.content.service.ReportService;
import com.xiyou.speakToMe.content.service.AuditLogService;
import com.xiyou.speakToMe.content.client.WechatContentClient;
import com.xiyou.speakToMe.canteen.dto.ReviewCreateReq;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.util.AesGcmUtil;
import com.xiyou.speakToMe.framework.exception.GlobalExceptionHandler;
import com.xiyou.speakToMe.framework.security.UserContext;
import com.xiyou.speakToMe.user.mapper.UserMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 10/16：真实 Mapper、事务代理和 H2 验证举报到处理的流程；Redis 仅模拟去重与失效。 */
class ModerationFlowTest {
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private StringRedisTemplate redis;
    private SensitiveWordFilter filter;
    private ReviewService reviewService;
    private WechatContentClient wechat;
    private final Set<String> duplicateKeys = new HashSet<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        // 10/16：列与一期真实表对应，保留默认值和唯一约束，避免假 Mapper 隐藏 SQL 错误。
        jdbc.execute("""
                CREATE TABLE user (id BIGINT PRIMARY KEY, openid_cipher VARCHAR(512), openid_hash VARCHAR(64),
                  unionid_cipher VARCHAR(512), nickname VARCHAR(64), avatar_url VARCHAR(512), campus_id BIGINT,
                  phone_cipher VARCHAR(512), phone_hash VARCHAR(64), role INT, status INT,
                  last_login_at TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE review (id BIGINT AUTO_INCREMENT PRIMARY KEY, module VARCHAR(32), target_type VARCHAR(32),
                  target_id BIGINT, user_id BIGINT, rating INT, content VARCHAR(500), images VARCHAR(500),
                  audit_status INT, audit_result VARCHAR(128), report_count INT DEFAULT 0, is_deleted INT DEFAULT 0,
                  deleted_at TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE report (id BIGINT AUTO_INCREMENT PRIMARY KEY, review_id BIGINT, reporter_id BIGINT,
                  reason_type INT, reason_detail VARCHAR(200), status INT, handle_result VARCHAR(200) DEFAULT '',
                  handler_id BIGINT, handled_at TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE sensitive_word (id BIGINT AUTO_INCREMENT PRIMARY KEY, word VARCHAR(64) UNIQUE,
                  category INT, level INT, status INT, source VARCHAR(32),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE audit_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, review_id BIGINT, user_id BIGINT,
                  content VARCHAR(500), check_type INT, hit_word VARCHAR(64), risk_level INT,
                  wx_err_code INT, wx_err_msg VARCHAR(512), result INT, create_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE campus (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(32), sort INT DEFAULT 0,
                  status INT DEFAULT 1, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE site (id BIGINT AUTO_INCREMENT PRIMARY KEY, campus_id BIGINT, name VARCHAR(64),
                  site_type INT DEFAULT 1, location VARCHAR(255), open_time VARCHAR(64), cover_url VARCHAR(512),
                  avg_rating DECIMAL(2,1) DEFAULT 0, rating_count INT DEFAULT 0, sort INT DEFAULT 0,
                  status INT DEFAULT 1, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE stall (id BIGINT AUTO_INCREMENT PRIMARY KEY, site_id BIGINT, name VARCHAR(64),
                  floor VARCHAR(16), category VARCHAR(32), sort INT DEFAULT 0, status INT DEFAULT 1,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE dish (id BIGINT AUTO_INCREMENT PRIMARY KEY, stall_id BIGINT, name VARCHAR(64),
                  description VARCHAR(255), price DECIMAL(8,2), avg_rating DECIMAL(2,1) DEFAULT 0,
                  rating_count INT DEFAULT 0, is_available INT DEFAULT 1, sort INT DEFAULT 0,
                  status INT DEFAULT 1, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.update("INSERT INTO user (id, nickname, role, status) VALUES (1, '管理员', 1, 1), (2, '举报人', 0, 1), (3, '作者', 0, 1)");
        jdbc.update("INSERT INTO campus (id, name) VALUES (1, '长安校区')");
        jdbc.update("INSERT INTO site (id, campus_id, name) VALUES (1, 1, '食堂')");
        jdbc.update("INSERT INTO stall (id, site_id, name) VALUES (1, 1, '面食')");
        jdbc.update("INSERT INTO dish (id, stall_id, name, price, avg_rating, rating_count) VALUES (10, 1, '牛肉面', 12, 5, 1)");
        jdbc.update("""
                INSERT INTO review (id, module, target_type, target_id, user_id, rating, content, audit_status)
                VALUES (1, 'food', 'dish', 10, 3, 5, '味道不错，分量充足', 1),
                       (2, 'food', 'dish', 10, 3, 3, '等待人工复审的内容', 3)
                """);
        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        for (Class<?> mapper : new Class<?>[]{UserMapper.class, ReviewMapper.class, ReportMapper.class,
                SensitiveWordMapper.class, AuditLogMapper.class, CampusMapper.class, SiteMapper.class, StallMapper.class, DishMapper.class}) {
            config.addMapper(mapper);
        }
        MybatisPlusInterceptor pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        factory.setPlugins(pagination);
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        UserMapper users = session.getMapper(UserMapper.class);
        ReviewMapper reviews = session.getMapper(ReviewMapper.class);
        DishMapper dishes = session.getMapper(DishMapper.class);
        ReportMapper reports = session.getMapper(ReportMapper.class);
        SensitiveWordMapper words = session.getMapper(SensitiveWordMapper.class);
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenAnswer(invocation -> duplicateKeys.add(invocation.getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(invocation -> duplicateKeys.remove(invocation.getArgument(0)));
        filter = new SensitiveWordFilter(words);
        DishService dishService = new DishService(dishes, reviews, redis);
        // 10/21：补齐真实审核日志 Mapper 与事务代理，验证拒绝响应不会把审计记录回滚。
        wechat = mock(WechatContentClient.class);
        reviewService = new ReviewService(reviews, dishes, users, filter, wechat,
                new AuditLogService(session.getMapper(AuditLogMapper.class)), dishService);
        String aesKey = AesGcmUtil.generateKey();
        ReflectionTestUtils.setField(reviewService, "aesKey", aesKey);
        ReflectionTestUtils.setField(reviewService, "contentMin", 10);
        ReflectionTestUtils.setField(reviewService, "contentMax", 200);
        jdbc.update("UPDATE user SET openid_cipher=? WHERE id=3", AesGcmUtil.encrypt("测试作者身份", aesKey));
        reviewService = transactional(reviewService, ds);
        AdminGuard guard = new AdminGuard(users);
        mvc = MockMvcBuilders.standaloneSetup(
                        new ReportController(transactional(new ReportService(reports, redis), ds)),
                        transactional(new AdminReportController(guard, reports, reviews, dishService), ds),
                        transactional(new AdminAuditController(guard, reviews, dishService), ds),
                        new AdminSensitiveWordController(guard, words, filter),
                        new AdminObjectController(guard, session.getMapper(CampusMapper.class),
                                session.getMapper(SiteMapper.class), session.getMapper(StallMapper.class), dishes),
                        new DishController(dishService, reviewService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        UserContext.set(2L, "举报人");
    }

    private ReviewCreateReq submission() {
        ReviewCreateReq req = new ReviewCreateReq();
        req.setModule("food");
        req.setTargetType("dish");
        req.setTargetId(10L);
        req.setRating(1);
        req.setContent("这是一条用于验证审核链路的点评正文");
        return req;
    }

    @Test
    void unavailableWechatCommitsManualReviewAndLogWithoutPublishingOrRating() {
        when(wechat.checkText(anyString(), anyString(), anyString()))
                .thenThrow(new BizException(ErrorCode.SYSTEM_ERROR, "模拟审核服务不可用"));
        var result = reviewService.submit(3L, submission());
        assertEquals(3, result.getAuditStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE review_id=? AND risk_level=1", Integer.class, result.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT rating_count FROM dish WHERE id=10", Integer.class));
        assertEquals(1, reviewService.listByDish(10L, 2L, 1, 10, "new").getTotal());
        assertEquals(3, reviewService.listByDish(10L, 3L, 1, 10, "new").getTotal());
    }

    @Test
    void riskyWechatKeepsBlockedRecordAndAuditLogAfterErrorResponse() {
        when(wechat.checkText(anyString(), anyString(), anyString()))
                .thenReturn(new WechatContentClient.CheckResult(false, false, true, 1, "risky"));
        assertEquals(ErrorCode.CONTENT_BLOCKED,
                assertThrows(BizException.class, () -> reviewService.submit(3L, submission())).getCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE audit_status=2", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE result=0 AND check_type=2", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT rating_count FROM dish WHERE id=10", Integer.class));
    }

    @Test
    void localBlockKeepsLogWithoutSendingContentToWechat() {
        jdbc.update("INSERT INTO sensitive_word(word,category,level,status) VALUES ('审核链路',1,1,1)");
        filter.reload();
        assertEquals(ErrorCode.CONTENT_BLOCKED,
                assertThrows(BizException.class, () -> reviewService.submit(3L, submission())).getCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE check_type=1 AND result=0", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM review", Integer.class));
        verifyNoInteractions(wechat);
    }

    @Test
    void localManualWordCannotBeOverriddenByWechatPass() {
        jdbc.update("INSERT INTO sensitive_word(word,category,level,status) VALUES ('审核链路',1,2,1)");
        filter.reload();
        when(wechat.checkText(anyString(), anyString(), anyString()))
                .thenReturn(new WechatContentClient.CheckResult(true, false, false, 0, "pass"));
        assertEquals(3, reviewService.submit(3L, submission()).getAuditStatus());
        assertEquals(1, jdbc.queryForObject("SELECT rating_count FROM dish WHERE id=10", Integer.class));
    }

    @Test
    void databaseFailureStillRollsBackReviewAndAuditTogether() {
        when(wechat.checkText(anyString(), anyString(), anyString()))
                .thenReturn(new WechatContentClient.CheckResult(false, true, false, 0, "review"));
        jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT reject_test_log CHECK (user_id <> 3)");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> reviewService.submit(3L, submission()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM review", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class));
    }

    // 10/16：测试保留生产事务边界，验证出错后工单、审核状态和评分同时回滚。
    @SuppressWarnings("unchecked")
    private <T> T transactional(T target, DataSource ds) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),
                new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }

    @AfterEach
    void clear() { UserContext.clear(); }

    @Test
    void reportBlockAndRestoreChangesVisibilityAndRating() throws Exception {
        mvc.perform(post("/api/v1/reports").contentType("application/json")
                        .content("{\"reviewId\":1,\"reporterId\":1,\"reasonType\":3,\"reasonDetail\":\"攻击他人\"}"))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(2L, jdbc.queryForObject("SELECT reporter_id FROM report", Long.class));
        assertEquals(1, jdbc.queryForObject("SELECT report_count FROM review WHERE id=1", Integer.class));
        mvc.perform(post("/api/v1/reports").contentType("application/json")
                .content("{\"reviewId\":1,\"reasonType\":3}")).andExpect(jsonPath("$.code").value(30001));
        UserContext.set(1L, "管理员");
        mvc.perform(get("/api/v1/admin/reports")).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/admin/reviews/1")).andExpect(jsonPath("$.data.content").value("味道不错，分量充足"));
        mvc.perform(post("/api/v1/admin/reports/1/handle").contentType("application/json")
                .content("{\"action\":\"block\",\"handleResult\":\"核实违规\"}")).andExpect(jsonPath("$.code").value(0));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM report WHERE id=1", Integer.class));
        assertEquals(1L, jdbc.queryForObject("SELECT handler_id FROM report WHERE id=1", Long.class));
        mvc.perform(post("/api/v1/admin/reports/1/handle").contentType("application/json")
                .content("{\"action\":\"dismiss\"}")).andExpect(jsonPath("$.code").value(20001));
        assertEquals(0, jdbc.queryForObject("SELECT rating_count FROM dish WHERE id=10", Integer.class));
        mvc.perform(get("/api/v1/dishes/10/reviews")).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(post("/api/v1/admin/audit/1/confirm").contentType("application/json")
                .content("{\"action\":\"pass\"}")).andExpect(jsonPath("$.code").value(0));
        assertEquals(1, jdbc.queryForObject("SELECT rating_count FROM dish WHERE id=10", Integer.class));
        assertEquals(5.0, jdbc.queryForObject("SELECT avg_rating FROM dish WHERE id=10", Double.class));
        mvc.perform(get("/api/v1/dishes/10/reviews")).andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void invalidReportDoesNotConsumeDuplicateQuotaAndInsertFailureRollsBackCount() throws Exception {
        mvc.perform(post("/api/v1/reports").contentType("application/json")
                .content("{\"reviewId\":2,\"reasonType\":3}")).andExpect(jsonPath("$.code").value(20004));
        assertTrue(duplicateKeys.isEmpty());
        mvc.perform(post("/api/v1/reports").contentType("application/json")
                .content("{\"reviewId\":1,\"reasonType\":7}")).andExpect(jsonPath("$.code").value(20001));
        jdbc.execute("ALTER TABLE report ADD CONSTRAINT reject_reports CHECK (reason_type <> 3)");
        mvc.perform(post("/api/v1/reports").contentType("application/json")
                .content("{\"reviewId\":1,\"reasonType\":3}")).andExpect(jsonPath("$.code").value(50001));
        assertEquals(0, jdbc.queryForObject("SELECT report_count FROM review WHERE id=1", Integer.class));
        assertTrue(duplicateKeys.isEmpty());
    }

    @Test
    void failedBlockRollsBackTicketReviewAndAggregate() throws Exception {
        jdbc.update("INSERT INTO report (id, review_id, reporter_id, reason_type, status) VALUES (1, 1, 2, 3, 0)");
        doThrow(new IllegalStateException("模拟缓存失效失败")).when(redis).delete("food:dish:10:stat");
        UserContext.set(1L, "管理员");
        mvc.perform(post("/api/v1/admin/reports/1/handle").contentType("application/json")
                .content("{\"action\":\"block\"}")).andExpect(jsonPath("$.code").value(50001));
        assertEquals(0, jdbc.queryForObject("SELECT status FROM report WHERE id=1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT audit_status FROM review WHERE id=1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT rating_count FROM dish WHERE id=10", Integer.class));
    }

    @Test
    void reviewQueueIsStableAndManualReviewUpdatesAggregate() throws Exception {
        // 10/16：复审前作者可看见自己的记录，其他人和游客只能看公开记录。
        mvc.perform(get("/api/v1/dishes/10/reviews")).andExpect(jsonPath("$.data.total").value(1));
        UserContext.clear();
        mvc.perform(get("/api/v1/dishes/10/reviews")).andExpect(jsonPath("$.data.total").value(1));
        UserContext.set(3L, "作者");
        mvc.perform(get("/api/v1/dishes/10/reviews")).andExpect(jsonPath("$.data.total").value(2));
        UserContext.set(1L, "管理员");
        mvc.perform(get("/api/v1/admin/reviews")).andExpect(jsonPath("$.data.list[0].id").value(2));
        mvc.perform(post("/api/v1/admin/audit/2/confirm").contentType("application/json")
                .content("{\"action\":\"pass\"}")).andExpect(jsonPath("$.code").value(0));
        assertEquals(4.0, jdbc.queryForObject("SELECT avg_rating FROM dish WHERE id=10", Double.class));
        mvc.perform(get("/api/v1/admin/reviews")).andExpect(jsonPath("$.data.total").value(0));
        jdbc.update("UPDATE review SET is_deleted=1 WHERE id=2");
        mvc.perform(post("/api/v1/admin/audit/2/confirm").contentType("application/json")
                .content("{\"action\":\"pass\"}")).andExpect(jsonPath("$.code").value(20004));
    }

    @Test
    void sensitiveWordsCanBeEditedDisabledAndRestoredWithImmediateEffect() throws Exception {
        UserContext.set(1L, "管理员");
        mvc.perform(post("/api/v1/admin/sensitive-words").contentType("application/json")
                .content("{\"word\":\"TEST词\",\"category\":6,\"level\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        assertTrue(filter.match("ＴＥＳＴ词").hasLevel1());
        mvc.perform(put("/api/v1/admin/sensitive-words/1").contentType("application/json")
                .content("{\"word\":\"测试词\",\"category\":3,\"level\":2,\"status\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        assertFalse(filter.match("test词").hasLevel1());
        assertTrue(filter.match("测 试 词").hasLevel2());
        mvc.perform(delete("/api/v1/admin/sensitive-words/1")).andExpect(jsonPath("$.code").value(0));
        assertFalse(filter.match("测试词").hasLevel2());
        mvc.perform(put("/api/v1/admin/sensitive-words/1").contentType("application/json")
                .content("{\"word\":\"测试词\",\"category\":3,\"level\":1,\"status\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        assertTrue(filter.match("测试词").hasLevel1());
        mvc.perform(post("/api/v1/admin/sensitive-words").contentType("application/json")
                .content("{\"word\":\"测试词\",\"category\":3,\"level\":1}"))
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void objectMaintenanceKeepsRatingFieldsAndValidatesParents() throws Exception {
        UserContext.set(1L, "管理员");
        mvc.perform(put("/api/v1/admin/dishes/10").contentType("application/json")
                .content("{\"name\":\"新名称\",\"stallId\":1,\"status\":0,\"price\":null,\"avgRating\":1,\"ratingCount\":999}"))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(5.0, jdbc.queryForObject("SELECT avg_rating FROM dish WHERE id=10", Double.class));
        assertNull(jdbc.queryForObject("SELECT price FROM dish WHERE id=10", Object.class));
        mvc.perform(get("/api/v1/admin/dishes")).andExpect(jsonPath("$.data.list[0].status").value(0));
        mvc.perform(get("/api/v1/stalls/1/dishes")).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(post("/api/v1/admin/sites").contentType("application/json")
                .content("{\"name\":\"新地点\",\"campusId\":999}"))
                .andExpect(jsonPath("$.code").value(20004));
        mvc.perform(post("/api/v1/admin/dishes").contentType("application/json")
                .content("{\"name\":\"新菜\",\"stallId\":1,\"price\":-1}"))
                .andExpect(jsonPath("$.code").value(20001));
        mvc.perform(get("/api/v1/admin/object-options"))
                .andExpect(jsonPath("$.data.sites[0].campusId").value(1));
    }

    @Test
    void everyManagementAreaRejectsOrdinaryAndBannedUsers() throws Exception {
        for (String area : new String[]{"reports", "reviews", "reviews/1", "campuses", "sites", "stalls", "dishes", "object-options", "sensitive-words"}) {
            mvc.perform(get("/api/v1/admin/" + area)).andExpect(jsonPath("$.code").value(10003));
        }
        for (String area : new String[]{"campuses", "sites", "stalls", "dishes", "sensitive-words"}) {
            mvc.perform(post("/api/v1/admin/" + area).contentType("application/json").content("{\"name\":\"越权\"}"))
                    .andExpect(jsonPath("$.code").value(10003));
            mvc.perform(put("/api/v1/admin/" + area + "/1").contentType("application/json").content("{\"name\":\"越权\"}"))
                    .andExpect(jsonPath("$.code").value(10003));
        }
        jdbc.update("UPDATE user SET status=0 WHERE id=1");
        UserContext.set(1L, "已封禁管理员");
        mvc.perform(get("/api/v1/admin/reviews")).andExpect(jsonPath("$.code").value(10003));
        UserContext.clear();
        mvc.perform(post("/api/v1/reports").contentType("application/json")
                .content("{\"reviewId\":1,\"reasonType\":3}")).andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    void fourLevelObjectsCanBeCreatedEditedDisabledAndRestored() throws Exception {
        UserContext.set(1L, "管理员");
        long campus = create("campuses", "{\"name\":\"新校区\",\"status\":0}");
        mvc.perform(put("/api/v1/admin/campuses/" + campus).contentType("application/json")
                .content("{\"name\":\"新校区启用\",\"status\":1,\"sort\":2}"))
                .andExpect(jsonPath("$.code").value(0));
        long site = create("sites", "{\"name\":\"新食堂\",\"campusId\":" + campus + "}");
        mvc.perform(put("/api/v1/admin/sites/" + site).contentType("application/json")
                .content("{\"name\":\"新食堂位置\",\"campusId\":" + campus + ",\"location\":\"东侧\",\"status\":0}"))
                .andExpect(jsonPath("$.code").value(0));
        long stall = create("stalls", "{\"name\":\"新档口\",\"siteId\":" + site + "}");
        mvc.perform(put("/api/v1/admin/stalls/" + stall).contentType("application/json")
                .content("{\"name\":\"新档口改名\",\"siteId\":" + site + ",\"floor\":\"2F\",\"status\":0}"))
                .andExpect(jsonPath("$.code").value(0));
        long dish = create("dishes", "{\"name\":\"新菜品\",\"stallId\":" + stall + ",\"price\":8.50,\"status\":0}");
        mvc.perform(put("/api/v1/admin/dishes/" + dish).contentType("application/json")
                .content("{\"name\":\"新菜品上架\",\"stallId\":" + stall + ",\"price\":9.50,\"status\":1,\"isAvailable\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM campus WHERE id=?", Integer.class, campus));
        assertEquals("东侧", jdbc.queryForObject("SELECT location FROM site WHERE id=?", String.class, site));
        assertEquals(0, jdbc.queryForObject("SELECT status FROM stall WHERE id=?", Integer.class, stall));
        assertEquals(9.50, jdbc.queryForObject("SELECT price FROM dish WHERE id=?", Double.class, dish));
        mvc.perform(get("/api/v1/admin/sites")).andExpect(jsonPath("$.data.total").value(2));
    }

    private long create(String area, String body) throws Exception {
        String response = mvc.perform(post("/api/v1/admin/" + area).contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("data").asLong();
    }

    @Test
    void invalidManagementFiltersAndLongReportDetailsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/reports").contentType("application/json")
                .content("{\"reviewId\":1,\"reasonType\":3,\"reasonDetail\":\"" + "字".repeat(201) + "\"}"))
                .andExpect(jsonPath("$.code").value(20001));
        assertTrue(duplicateKeys.isEmpty());
        UserContext.set(1L, "管理员");
        mvc.perform(get("/api/v1/admin/reports?status=3")).andExpect(jsonPath("$.code").value(20001));
        mvc.perform(get("/api/v1/admin/reviews?auditStatus=0")).andExpect(jsonPath("$.code").value(20001));
        mvc.perform(get("/api/v1/admin/sensitive-words?size=51")).andExpect(jsonPath("$.code").value(20001));
        mvc.perform(get("/api/v1/admin/campuses?page=0")).andExpect(jsonPath("$.code").value(20001));
        mvc.perform(get("/api/v1/admin/reviews/999")).andExpect(jsonPath("$.code").value(20004));
    }
}
