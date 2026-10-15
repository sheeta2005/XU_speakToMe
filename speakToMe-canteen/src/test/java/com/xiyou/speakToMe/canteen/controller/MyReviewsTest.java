package com.xiyou.speakToMe.canteen.controller;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.xiyou.speakToMe.canteen.mapper.DishMapper;
import com.xiyou.speakToMe.canteen.mapper.ReviewMapper;
import com.xiyou.speakToMe.canteen.service.DishService;
import com.xiyou.speakToMe.canteen.service.ReviewService;
import com.xiyou.speakToMe.framework.exception.GlobalExceptionHandler;
import com.xiyou.speakToMe.framework.security.UserContext;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 10/15：通过真实 Mapper 和内存数据库验证接口，避免只检查查询条件字符串。 */
class MyReviewsTest {

    private SqlSession session;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE review (
                  id BIGINT PRIMARY KEY, module VARCHAR(32), target_type VARCHAR(32), target_id BIGINT,
                  user_id BIGINT, rating INT, content VARCHAR(500), images VARCHAR(500), audit_status INT,
                  audit_result VARCHAR(128), report_count INT, is_deleted INT, deleted_at TIMESTAMP,
                  created_at TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE dish (
                  id BIGINT PRIMARY KEY, stall_id BIGINT, name VARCHAR(64), description VARCHAR(255),
                  price DECIMAL(8,2), avg_rating DECIMAL(2,1), rating_count INT, is_available INT,
                  sort INT, status INT, created_at TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.update("INSERT INTO dish (id, name, status) VALUES (10, '牛肉面', 1)");
        // 10/15：包含全部审核状态、其他用户、已删除和其他模块，验证历史记录边界。
        for (int id = 1; id <= 4; id++) {
            jdbc.update("""
                    INSERT INTO review (id, module, target_type, target_id, user_id, rating, content,
                      audit_status, is_deleted, created_at)
                    VALUES (?, 'food', 'dish', ?, 1, 5, '味道不错，分量充足', ?, 0, '2026-10-15 10:00:00')
                    """, id, id == 4 ? 99 : 10, id - 1);
        }
        jdbc.update("""
                INSERT INTO review (id, module, target_type, target_id, user_id, audit_status, is_deleted)
                VALUES (5, 'food', 'dish', 10, 2, 1, 0),
                       (6, 'food', 'dish', 10, 1, 1, 1),
                       (7, 'trade', 'dish', 10, 1, 1, 0),
                       (8, 'food', 'site', 10, 1, 1, 0)
                """);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ReviewMapper.class);
        configuration.addMapper(DishMapper.class);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setPlugins(interceptor);
        session = factory.getObject().openSession(true);
        ReviewService service = new ReviewService(session.getMapper(ReviewMapper.class),
                session.getMapper(DishMapper.class), null, null, null, null, mock(DishService.class));
        mvc = MockMvcBuilders.standaloneSetup(new ReviewController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        if (session != null) session.close();
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/reviews/mine")).andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    void currentUserCannotOverrideOwnerAndSeesAllOwnAuditStates() throws Exception {
        UserContext.set(1L, "test");
        mvc.perform(get("/api/v1/reviews/mine").param("userId", "2"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.list.length()").value(4))
                .andExpect(jsonPath("$.data.list[0].id").value(4))
                .andExpect(jsonPath("$.data.list[0].dishName").value("菜品已移除"))
                .andExpect(jsonPath("$.data.list[1].dishName").value("牛肉面"))
                .andExpect(jsonPath("$.data.list[0].auditStatus").value(3))
                .andExpect(jsonPath("$.data.list[1].auditStatus").value(2))
                .andExpect(jsonPath("$.data.list[2].auditStatus").value(1))
                .andExpect(jsonPath("$.data.list[3].auditStatus").value(0));
    }

    @Test
    void equalTimestampsHaveStablePaginationAndEmptyPagesWork() throws Exception {
        UserContext.set(1L, "test");
        mvc.perform(get("/api/v1/reviews/mine").param("page", "2").param("size", "2"))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.list[0].id").value(2))
                .andExpect(jsonPath("$.data.list[1].id").value(1));
        mvc.perform(get("/api/v1/reviews/mine").param("page", "3").param("size", "2"))
                .andExpect(jsonPath("$.data.list.length()").value(0));
        UserContext.set(3L, "test");
        mvc.perform(get("/api/v1/reviews/mine"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @ParameterizedTest
    @CsvSource({"0,10", "-1,10", "1,0", "1,51"})
    void invalidPaginationIsRejected(int page, int size) throws Exception {
        UserContext.set(1L, "test");
        mvc.perform(get("/api/v1/reviews/mine").param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void deletionOnlyRemovesCurrentUsersReviewFromTheList() throws Exception {
        UserContext.set(1L, "test");
        mvc.perform(delete("/api/v1/reviews/5")).andExpect(jsonPath("$.code").value(20005));
        mvc.perform(delete("/api/v1/reviews/4")).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/v1/reviews/mine"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list[0].id").value(3));
    }
}
