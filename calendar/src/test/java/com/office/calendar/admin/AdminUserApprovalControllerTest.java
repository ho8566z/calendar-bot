package com.office.calendar.admin;

import com.office.calendar.admin.AdminUserApprovalService.RoleChangeResult;
import com.office.calendar.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 보안 설정 + 컨트롤러 + 상태 코드 매핑을 DB 없이 함께 확인한다(서비스는 가짜)
@SpringJUnitWebConfig(classes = {SecurityConfig.class, AdminUserApprovalControllerTest.WebConfig.class})
class AdminUserApprovalControllerTest {

    @Configuration
    @EnableWebMvc
    static class WebConfig {

        @Bean
        AdminUserApprovalService approvalService() {
            return Mockito.mock(AdminUserApprovalService.class);
        }

        @Bean
        AdminUserApprovalController approvalController(AdminUserApprovalService approvalService) {
            return new AdminUserApprovalController(approvalService);
        }

    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AdminUserApprovalService approvalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(approvalService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("승인 성공이면 200과 result 1, 바뀐 역할, 수정 시각을 돌려준다")
    void approve_ok() throws Exception {
        when(approvalService.approve(5)).thenReturn(RoleChangeResult.changed("USER", "2026-11-08 10:30:00"));

        mockMvc.perform(post("/admin/users/5/approve").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(1))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.mod_date").value("2026-11-08 10:30:00"));
    }

    @Test
    @DisplayName("승인 취소 성공이면 200과 PRE_USER를 돌려준다")
    void unapprove_ok() throws Exception {
        when(approvalService.unapprove(5)).thenReturn(RoleChangeResult.changed("PRE_USER", "2026-11-08 10:31:00"));

        mockMvc.perform(post("/admin/users/5/unapprove").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(1))
                .andExpect(jsonPath("$.role").value("PRE_USER"));
    }

    @Test
    @DisplayName("없는 회원이면 404와 MEMBER_NOT_FOUND")
    void approve_notFound() throws Exception {
        when(approvalService.approve(404)).thenReturn(RoleChangeResult.notFound());

        mockMvc.perform(post("/admin/users/404/approve").with(user("boss").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value(0))
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("상태가 맞지 않으면 409와 INVALID_STATUS, 지금 역할을 알려 준다")
    void approve_invalidStatus() throws Exception {
        when(approvalService.approve(7)).thenReturn(RoleChangeResult.invalidStatus("ADMIN"));

        mockMvc.perform(post("/admin/users/7/approve").with(user("boss").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.result").value(0))
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("일반 사용자는 승인할 수 없다(서비스도 호출되지 않는다)")
    void approve_user() throws Exception {
        mockMvc.perform(post("/admin/users/5/approve").with(user("alice").roles("USER")))
                .andExpect(redirectedUrl("/member/access_denied"));

        verify(approvalService, never()).approve(Mockito.anyInt());
    }

    @Test
    @DisplayName("일반 사용자는 승인 취소도 할 수 없다")
    void unapprove_user() throws Exception {
        mockMvc.perform(post("/admin/users/5/unapprove").with(user("alice").roles("USER")))
                .andExpect(redirectedUrl("/member/access_denied"));

        verify(approvalService, never()).unapprove(Mockito.anyInt());
    }

    @Test
    @DisplayName("로그인하지 않으면 로그인 화면으로 보낸다")
    void approve_anonymous() throws Exception {
        mockMvc.perform(post("/admin/users/5/approve"))
                .andExpect(redirectedUrl("/member/signin"));
    }

}
