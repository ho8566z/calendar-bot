package com.office.calendar.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 경로별 접근 권한(sec-002): /planner/** = USER, /admin/** = ADMIN
// DB 없이 보안 설정만 올려서 확인한다. 보안을 통과한 요청은 컨트롤러가 없어 404가 되므로 '404 = 통과'로 읽는다.
@SpringJUnitWebConfig(classes = {SecurityConfig.class, SecurityRulesTest.WebConfig.class})
class SecurityRulesTest {

    @Configuration
    @EnableWebMvc
    static class WebConfig {
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("로그인하지 않으면 /admin/** 는 로그인 화면으로 보낸다")
    void admin_anonymous() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/signin"));
    }

    @Test
    @DisplayName("일반 사용자(USER)는 /admin/** 에 들어갈 수 없다")
    void admin_user() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("alice").roles("USER")))
                .andExpect(redirectedUrl("/member/access_denied"));
    }

    @Test
    @DisplayName("미승인 회원(PRE_USER)은 /admin/** 에 들어갈 수 없다")
    void admin_preUser() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("bob").roles("PRE_USER")))
                .andExpect(redirectedUrl("/member/access_denied"));
    }

    @Test
    @DisplayName("로그인하지 않아도 로그인 결과 화면(/member/signin_result)을 볼 수 있다(실패·잠금 안내)")
    void signinResult_anonymous() throws Exception {
        mockMvc.perform(get("/member/signin_result").param("locked", "true"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("관리자(ADMIN)는 /admin/** 를 통과한다")
    void admin_admin() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("boss").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("관리자(ADMIN)는 일정 기능(/planner/**)에 들어갈 수 없다")
    void planner_admin() throws Exception {
        mockMvc.perform(get("/planner").with(user("boss").roles("ADMIN")))
                .andExpect(redirectedUrl("/member/access_denied"));
    }

    @Test
    @DisplayName("사용자(USER)는 일정 기능(/planner/**)을 통과한다")
    void planner_user() throws Exception {
        mockMvc.perform(get("/planner").with(user("alice").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("미승인 회원(PRE_USER)은 일정 기능에 들어갈 수 없다")
    void planner_preUser() throws Exception {
        mockMvc.perform(get("/planner").with(user("bob").roles("PRE_USER")))
                .andExpect(redirectedUrl("/member/access_denied"));
    }

    @Test
    @DisplayName("로그인하지 않으면 일정 기능은 로그인 화면으로 보낸다")
    void planner_anonymous() throws Exception {
        mockMvc.perform(get("/planner"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/signin"));
    }

    @Test
    @DisplayName("홈과 로그인 화면은 로그인 없이 통과한다")
    void publicPaths() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isNotFound());
        mockMvc.perform(get("/member/signin")).andExpect(status().isNotFound());
    }

}
