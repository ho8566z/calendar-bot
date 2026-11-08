package com.office.calendar.brief;

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

import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// "지금 보내기" API: 응답 형식, 오류 코드, 접근 제어
@SpringJUnitWebConfig(classes = {SecurityConfig.class, BriefControllerTest.WebConfig.class})
class BriefControllerTest {

    @Configuration
    @EnableWebMvc
    static class WebConfig {

        @Bean
        BriefNowService briefNowService() {
            return Mockito.mock(BriefNowService.class);
        }

        @Bean
        BriefController briefController(BriefNowService service) {
            return new BriefController(service);
        }

    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private BriefNowService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(service);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static BriefNowService.Result result(BriefNowService.Outcome outcome) {
        return new BriefNowService.Result(outcome, null, false, 0, false);
    }

    @Test
    @DisplayName("보냄: 200과 대상 주·일정 없음 여부")
    void sent() throws Exception {
        when(service.sendNow("alice")).thenReturn(new BriefNowService.Result(BriefNowService.Outcome.SENT, LocalDate.of(2026, 11, 9), false, 0, false));

        mockMvc.perform(post("/member/brief/send").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.week_start").value("2026-11-09"))
                .andExpect(jsonPath("$.empty").value(false));
    }

    @Test
    @DisplayName("일정이 없어 안내만 보낸 경우 empty=true")
    void sentEmpty() throws Exception {
        when(service.sendNow("alice")).thenReturn(new BriefNowService.Result(BriefNowService.Outcome.SENT, LocalDate.of(2026, 11, 9), true, 0, false));

        mockMvc.perform(post("/member/brief/send").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empty").value(true));
    }

    @Test
    @DisplayName("봇 미설정은 503 TELEGRAM_NOT_CONFIGURED")
    void notConfigured() throws Exception {
        when(service.sendNow("alice")).thenReturn(result(BriefNowService.Outcome.NOT_CONFIGURED));

        mockMvc.perform(post("/member/brief/send").with(user("alice").roles("USER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TELEGRAM_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("미연동은 409 TELEGRAM_NOT_LINKED")
    void notLinked() throws Exception {
        when(service.sendNow("alice")).thenReturn(result(BriefNowService.Outcome.NOT_LINKED));

        mockMvc.perform(post("/member/brief/send").with(user("alice").roles("USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TELEGRAM_NOT_LINKED"));
    }

    @Test
    @DisplayName("너무 자주 누르면 429 TOO_FREQUENT와 남은 초")
    void tooFrequent() throws Exception {
        when(service.sendNow("alice")).thenReturn(new BriefNowService.Result(BriefNowService.Outcome.TOO_FREQUENT, null, false, 8, false));

        mockMvc.perform(post("/member/brief/send").with(user("alice").roles("USER")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_FREQUENT"))
                .andExpect(jsonPath("$.retry_after").value(8));
    }

    @Test
    @DisplayName("발송 실패는 502 SEND_FAILED, 봇을 차단했으면 BOT_BLOCKED")
    void sendFailed() throws Exception {
        when(service.sendNow("alice")).thenReturn(result(BriefNowService.Outcome.SEND_FAILED));
        mockMvc.perform(post("/member/brief/send").with(user("alice").roles("USER")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SEND_FAILED"));

        when(service.sendNow("alice")).thenReturn(new BriefNowService.Result(BriefNowService.Outcome.SEND_FAILED, null, false, 0, true));
        mockMvc.perform(post("/member/brief/send").with(user("alice").roles("USER")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BOT_BLOCKED"));
    }

    @Test
    @DisplayName("대상은 로그인한 본인뿐이다(요청 값으로 다른 회원을 지정할 수 없다)")
    void onlySelf() throws Exception {
        when(service.sendNow("alice")).thenReturn(new BriefNowService.Result(BriefNowService.Outcome.SENT, LocalDate.of(2026, 11, 9), false, 0, false));

        mockMvc.perform(post("/member/brief/send").param("member_id", "bob").param("id", "bob").with(user("alice").roles("USER")))
                .andExpect(status().isOk());

        Mockito.verify(service).sendNow("alice");
        Mockito.verifyNoMoreInteractions(service);
    }

    @Test
    @DisplayName("접근 제어: 비로그인은 로그인 화면, 승인 전(PRE_USER)·관리자는 접근 거부 화면")
    void access() throws Exception {
        mockMvc.perform(post("/member/brief/send")).andExpect(redirectedUrl("/member/signin"));
        mockMvc.perform(post("/member/brief/send").with(user("x").roles("PRE_USER"))).andExpect(redirectedUrl("/member/access_denied"));
        mockMvc.perform(post("/member/brief/send").with(user("x").roles("ADMIN"))).andExpect(redirectedUrl("/member/access_denied"));

        Mockito.verifyNoInteractions(service);
    }

}
