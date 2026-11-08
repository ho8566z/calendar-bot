package com.office.calendar.admin;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 보안 설정 + 컨트롤러 + JSON 직렬화를 DB 없이 함께 확인한다(서비스는 가짜)
@SpringJUnitWebConfig(classes = {SecurityConfig.class, AdminUserControllerTest.WebConfig.class})
class AdminUserControllerTest {

    @Configuration
    @EnableWebMvc
    static class WebConfig {

        @Bean
        AdminUserService adminUserService() {
            return Mockito.mock(AdminUserService.class);
        }

        @Bean
        AdminUserController adminUserController(AdminUserService adminUserService) {
            return new AdminUserController(adminUserService);
        }

    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AdminUserService adminUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(adminUserService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("관리자는 검색어·페이지를 넘겨 JSON 목록을 받고, 비밀번호는 응답에 없다")
    void data_admin() throws Exception {
        AdminUserDto alice = AdminUserDto.builder().no(1).id("alice").mail("a@x").phone("010").role("USER")
                .reg_date("2026-11-08 10:00:00").mod_date("2026-11-08 10:00:00").build();
        when(adminUserService.getUsers("ali", 1, 10))
                .thenReturn(Map.of("users", List.of(alice), "total", 11, "page", 1, "size", 10));

        String body = mockMvc.perform(get("/admin/users/data")
                        .param("keyword", "ali").param("page", "1").param("size", "10")
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(11))
                .andExpect(jsonPath("$.users[0].id").value("alice"))
                .andExpect(jsonPath("$.users[0].role").value("USER"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("\"pw\"").doesNotContain("password");
    }

    @Test
    @DisplayName("파라미터가 없으면 기본값(검색어 없음, 0페이지, 20건)으로 조회한다")
    void data_defaults() throws Exception {
        when(adminUserService.getUsers(null, 0, 20)).thenReturn(Map.of("users", List.of(), "total", 0));

        mockMvc.perform(get("/admin/users/data").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        verify(adminUserService).getUsers(null, 0, 20);
    }

    @Test
    @DisplayName("일반 사용자는 목록 데이터를 받을 수 없다(서비스도 호출되지 않는다)")
    void data_user() throws Exception {
        mockMvc.perform(get("/admin/users/data").with(user("alice").roles("USER")))
                .andExpect(redirectedUrl("/member/access_denied"));

        verify(adminUserService, never()).getUsers(Mockito.any(), Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    @DisplayName("로그인하지 않으면 로그인 화면으로 보낸다")
    void data_anonymous() throws Exception {
        mockMvc.perform(get("/admin/users/data"))
                .andExpect(redirectedUrl("/member/signin"));
    }

}
