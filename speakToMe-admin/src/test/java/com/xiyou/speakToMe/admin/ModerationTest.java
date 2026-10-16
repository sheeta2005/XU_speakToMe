package com.xiyou.speakToMe.admin;

import com.xiyou.speakToMe.admin.controller.AdminAuditController;
import com.xiyou.speakToMe.admin.controller.AdminReportController;
import com.xiyou.speakToMe.admin.security.AdminGuard;
import com.xiyou.speakToMe.canteen.entity.Review;
import com.xiyou.speakToMe.canteen.mapper.ReviewMapper;
import com.xiyou.speakToMe.canteen.service.DishService;
import com.xiyou.speakToMe.content.entity.Report;
import com.xiyou.speakToMe.content.mapper.ReportMapper;
import com.xiyou.speakToMe.framework.exception.GlobalExceptionHandler;
import com.xiyou.speakToMe.framework.security.UserContext;
import com.xiyou.speakToMe.user.entity.User;
import com.xiyou.speakToMe.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 10/16：先复现举报驳回状态、重复处理和人工下架后的评分刷新缺口。 */
class ModerationTest {
    private final UserMapper users = mock(UserMapper.class);
    private final ReportMapper reports = mock(ReportMapper.class);
    private final ReviewMapper reviews = mock(ReviewMapper.class);
    private final DishService dishes = mock(DishService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "测试"), Review.class);
        User admin = new User();
        admin.setId(1L);
        admin.setRole(1);
        admin.setStatus(1);
        when(users.selectById(1L)).thenReturn(admin);
        when(reports.update(any(), any())).thenReturn(1);
        when(reviews.update(any(), any())).thenReturn(1);
        AdminGuard guard = new AdminGuard(users);
        mvc = MockMvcBuilders.standaloneSetup(new AdminReportController(guard, reports, reviews, dishes),
                        new AdminAuditController(guard, reviews, dishes))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        UserContext.set(1L, "测试管理员");
    }

    @AfterEach
    void clear() { UserContext.clear(); }

    @Test
    void dismissMustBeStoredAsRejected() throws Exception {
        Report ticket = new Report();
        ticket.setId(9L);
        ticket.setStatus(0);
        when(reports.selectById(9L)).thenReturn(ticket);
        mvc.perform(post("/api/v1/admin/reports/9/handle").contentType("application/json")
                        .content("{\"action\":\"dismiss\",\"handleResult\":\"核实无违规\"}"))
                .andExpect(jsonPath("$.code").value(0));
        // 10/16：检查实际更新参数，驳回不能落到“已下架”的状态分组。
        verify(reports).update(argThat(r -> r != null && r.getStatus() == 2), any());
    }

    @Test
    void processedTicketCannotBeOverwritten() throws Exception {
        Report ticket = new Report();
        ticket.setId(9L);
        ticket.setStatus(2);
        when(reports.selectById(9L)).thenReturn(ticket);
        mvc.perform(post("/api/v1/admin/reports/9/handle").contentType("application/json")
                        .content("{\"action\":\"block\"}"))
                .andExpect(jsonPath("$.code").value(20001));
        verify(reports, never()).update(any(), any());
    }

    @Test
    void manualBlockRefreshesRatingAndDeletedReviewCannotBeRestored() throws Exception {
        Review review = new Review();
        review.setId(3L);
        review.setModule("food");
        review.setTargetType("dish");
        review.setTargetId(10L);
        review.setIsDeleted(0);
        review.setAuditStatus(1);
        when(reviews.selectOne(any())).thenReturn(review);
        mvc.perform(post("/api/v1/admin/audit/3/confirm").contentType("application/json")
                        .content("{\"action\":\"block\"}"))
                .andExpect(jsonPath("$.code").value(0));
        verify(dishes).refreshRating(10L);
        review.setIsDeleted(1);
        mvc.perform(post("/api/v1/admin/audit/3/confirm").contentType("application/json")
                        .content("{\"action\":\"pass\"}"))
                .andExpect(jsonPath("$.code").value(20004));
    }

    @Test
    void ordinaryUserAndAnonymousCannotAccessQueues() throws Exception {
        User ordinary = new User();
        ordinary.setRole(0);
        ordinary.setStatus(1);
        when(users.selectById(2L)).thenReturn(ordinary);
        UserContext.set(2L, "普通用户");
        mvc.perform(get("/api/v1/admin/reports")).andExpect(jsonPath("$.code").value(10003));
        mvc.perform(post("/api/v1/admin/audit/3/confirm").contentType("application/json")
                .content("{\"action\":\"pass\"}")).andExpect(jsonPath("$.code").value(10003));
        UserContext.clear();
        mvc.perform(get("/api/v1/admin/reviews")).andExpect(jsonPath("$.code").value(10001));
    }
}
