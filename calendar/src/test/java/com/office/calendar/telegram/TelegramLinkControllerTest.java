package com.office.calendar.telegram;

import com.office.calendar.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 텔레그램 연동 API: 응답 형식, 오류 코드, 접근 제어
@SpringJUnitWebConfig(classes = {SecurityConfig.class, TelegramLinkControllerTest.WebConfig.class})
class TelegramLinkControllerTest {

    @Configuration
    @EnableWebMvc
    static class WebConfig {

        @Bean
        TelegramLinkService telegramLinkService() {
            return Mockito.mock(TelegramLinkService.class);
        }

        @Bean
        TelegramLinkController telegramLinkController(TelegramLinkService service) {
            return new TelegramLinkController(service);
        }

    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TelegramLinkService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(service);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("발급: 코드·링크·만료 시각을 돌려준다")
    void issue_ok() throws Exception {
        when(service.issueCode("alice")).thenReturn(new TelegramLinkService.IssuedLink(
                TelegramLinkService.IssueOutcome.ISSUED, "CODE1234", "https://t.me/MyCalBot?start=CODE1234",
                LocalDateTime.of(2026, 11, 8, 12, 10, 0)));

        mockMvc.perform(post("/member/telegram/link").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CODE1234"))
                .andExpect(jsonPath("$.link").value("https://t.me/MyCalBot?start=CODE1234"))
                .andExpect(jsonPath("$.expires_at").value("2026-11-08 12:10:00"));
    }

    @Test
    @DisplayName("발급: 이미 연동됨 → 409 ALREADY_LINKED")
    void issue_alreadyLinked() throws Exception {
        when(service.issueCode("alice")).thenReturn(new TelegramLinkService.IssuedLink(
                TelegramLinkService.IssueOutcome.ALREADY_LINKED, null, null, null));

        mockMvc.perform(post("/member/telegram/link").with(user("alice").roles("USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_LINKED"));
    }

    @Test
    @DisplayName("발급: 봇 미설정 → 503 TELEGRAM_NOT_CONFIGURED")
    void issue_notConfigured() throws Exception {
        when(service.issueCode("alice")).thenReturn(new TelegramLinkService.IssuedLink(
                TelegramLinkService.IssueOutcome.NOT_CONFIGURED, null, null, null));

        mockMvc.perform(post("/member/telegram/link").with(user("alice").roles("USER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TELEGRAM_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("상태·해제: 로그인한 본인 기준이고 응답은 linked, brief_enabled")
    void statusAndUnlink() throws Exception {
        when(service.getStatus("alice")).thenReturn(new TelegramLinkService.Status(true, true));
        when(service.unlink("alice")).thenReturn(new TelegramLinkService.Status(false, false));

        mockMvc.perform(get("/member/telegram").param("member_id", "bob").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.brief_enabled").value(true));

        mockMvc.perform(delete("/member/telegram").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.brief_enabled").value(false));

        verify(service, never()).getStatus("bob");
    }

    @Test
    @DisplayName("수신 설정: enabled를 받아 저장하고 그대로 돌려준다")
    void brief_ok() throws Exception {
        when(service.setBriefEnabled("alice", true)).thenReturn(TelegramLinkService.BriefOutcome.CHANGED);

        mockMvc.perform(put("/member/brief").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": true}")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("수신 설정: 연동하지 않았으면 409 TELEGRAM_NOT_LINKED")
    void brief_notLinked() throws Exception {
        when(service.setBriefEnabled("alice", true)).thenReturn(TelegramLinkService.BriefOutcome.NOT_LINKED);

        mockMvc.perform(put("/member/brief").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": true}")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TELEGRAM_NOT_LINKED"));
    }

    @Test
    @DisplayName("수신 설정: enabled가 없거나 null이거나 불리언이 아니거나 본문이 없으면 400이고 저장하지 않는다")
    void brief_badBody() throws Exception {
        for (String body : new String[]{"{}", "{\"enabled\": null}", "{\"enabled\": \"yes\"}", "not json"}) {
            mockMvc.perform(put("/member/brief").contentType(MediaType.APPLICATION_JSON).content(body)
                            .with(user("alice").roles("USER")))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/member/brief").with(user("alice").roles("USER")))
                .andExpect(status().is4xxClientError());

        verify(service, never()).setBriefEnabled(anyString(), anyBoolean());
    }

    @Test
    @DisplayName("접근 제어: 비로그인은 로그인 화면으로, 승인 전(PRE_USER)·관리자는 접근 거부 화면으로 보낸다")
    void access() throws Exception {
        String[][] calls = {{"POST", "/member/telegram/link"}, {"GET", "/member/telegram"}, {"DELETE", "/member/telegram"}, {"PUT", "/member/brief"}};

        for (String[] c : calls) {
            var anonymous = switch (c[0]) {
                case "POST" -> post(c[1]);
                case "GET" -> get(c[1]);
                case "DELETE" -> delete(c[1]);
                default -> put(c[1]).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}");
            };
            mockMvc.perform(anonymous).andExpect(redirectedUrl("/member/signin"));

            for (String role : new String[]{"PRE_USER", "ADMIN"}) {
                var req = switch (c[0]) {
                    case "POST" -> post(c[1]);
                    case "GET" -> get(c[1]);
                    case "DELETE" -> delete(c[1]);
                    default -> put(c[1]).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}");
                };
                mockMvc.perform(req.with(user("x").roles(role))).andExpect(redirectedUrl("/member/access_denied"));
            }
        }

        Mockito.verifyNoInteractions(service);
    }

}
