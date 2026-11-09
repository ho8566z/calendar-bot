package com.office.calendar.member.security;

import com.office.calendar.config.SecurityConfig;
import com.office.calendar.member.jpa.LoginLockEntity;
import com.office.calendar.member.jpa.LoginLockId;
import com.office.calendar.member.jpa.LoginLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

// 로그인 시도 제한(sec-006)을 실제 SecurityConfig(필터·핸들러)로 확인한다. DB 없이 메모리 리포지토리를 쓴다.
// 올바른 비밀번호면 /member/signin_result?loginedID=..., 틀리면 /member/signin_result,
// 잠겨 있으면 /member/signin_result?locked=true 로 이동한다.
@SpringJUnitWebConfig(classes = {SecurityConfig.class, LoginLockSecurityTest.TestConfig.class})
class LoginLockSecurityTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final String GOOD = "good-pw";

    @Configuration
    @EnableWebMvc
    static class TestConfig {

        static final Map<LoginLockId, LoginLockEntity> TABLE = new HashMap<>();
        static final MutableClock CLOCK = new MutableClock();

        @Bean
        Clock clock() {
            return CLOCK;
        }

        @Bean
        LoginLockRepository loginLockRepository() {
            return InMemoryLoginLockRepository.create(TABLE);
        }

        @Bean
        LoginLockService loginLockService(LoginLockRepository repository, Clock clock) {
            return new LoginLockService(repository, clock, 5, 10);
        }

        @Bean
        UserDetailsService userDetailsService(PasswordEncoder encoder) {
            return new InMemoryUserDetailsManager(
                    User.withUsername("alice").password(encoder.encode(GOOD)).roles("USER").build());
        }
    }

    static class MutableClock extends Clock {
        private Instant instant = LocalDateTime.of(2026, 11, 9, 12, 0, 0).atZone(ZONE).toInstant();

        void plusMinutes(long minutes) {
            instant = instant.plusSeconds(minutes * 60);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TestConfig.TABLE.clear();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private MockHttpServletRequestBuilder signin(String id, String pw, String ip) {
        return post("/member/signin_confirm").param("id", id).param("pw", pw)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    private void wrong(String id, String ip, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            mockMvc.perform(signin(id, "wrong", ip)).andExpect(redirectedUrl("/member/signin_result"));
        }
    }

    @Test
    @DisplayName("4번 틀린 뒤 올바른 비밀번호는 로그인된다")
    void fourFailuresThenSuccess() throws Exception {
        wrong("alice", "1.1.1.1", 4);

        mockMvc.perform(signin("alice", GOOD, "1.1.1.1"))
                .andExpect(redirectedUrl("/member/signin_result?loginedID=alice"));
    }

    @Test
    @DisplayName("5번 틀리면 잠기고, 잠금 중에는 올바른 비밀번호도 거부한다")
    void lockedRejectsCorrectPassword() throws Exception {
        wrong("alice", "1.1.1.1", 5);

        mockMvc.perform(signin("alice", GOOD, "1.1.1.1"))
                .andExpect(redirectedUrl("/member/signin_result?locked=true"));
    }

    @Test
    @DisplayName("잠금 중의 시도는 실패 횟수를 더 올리지 않는다")
    void lockedAttemptsDoNotCount() throws Exception {
        wrong("alice", "1.1.1.1", 5);
        LoginLockId key = new LoginLockId("alice", "1.1.1.1");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(signin("alice", "wrong", "1.1.1.1"))
                    .andExpect(redirectedUrl("/member/signin_result?locked=true"));
        }

        assertThat(TestConfig.TABLE.get(key).getFailCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("10분이 지나면 다시 로그인할 수 있다")
    void unlockedAfterTenMinutes() throws Exception {
        wrong("alice", "1.1.1.1", 5);
        TestConfig.CLOCK.plusMinutes(10);

        mockMvc.perform(signin("alice", GOOD, "1.1.1.1"))
                .andExpect(redirectedUrl("/member/signin_result?loginedID=alice"));
    }

    @Test
    @DisplayName("로그인에 성공하면 실패 기록이 지워진다")
    void successClearsRecord() throws Exception {
        wrong("alice", "1.1.1.1", 3);

        mockMvc.perform(signin("alice", GOOD, "1.1.1.1"))
                .andExpect(redirectedUrl("/member/signin_result?loginedID=alice"));

        assertThat(TestConfig.TABLE).isEmpty();
    }

    @Test
    @DisplayName("없는 아이디도 같은 방식으로 세고, 같은 응답으로 잠근다")
    void unknownIdLockedLikeKnownId() throws Exception {
        wrong("nobody", "1.1.1.1", 5);

        mockMvc.perform(signin("nobody", "wrong", "1.1.1.1"))
                .andExpect(redirectedUrl("/member/signin_result?locked=true"));
    }

    @Test
    @DisplayName("다른 IP에서는 같은 아이디도 잠기지 않는다")
    void otherIpNotLocked() throws Exception {
        wrong("alice", "1.1.1.1", 5);

        mockMvc.perform(signin("alice", GOOD, "2.2.2.2"))
                .andExpect(redirectedUrl("/member/signin_result?loginedID=alice"));
    }

    @Test
    @DisplayName("20자를 넘는 아이디도 오류 없이 앞 20자 기준으로 센다")
    void longIdHandled() throws Exception {
        String longId = "x".repeat(40);
        wrong(longId, "1.1.1.1", 5);

        mockMvc.perform(signin(longId, "wrong", "1.1.1.1"))
                .andExpect(redirectedUrl("/member/signin_result?locked=true"));
        assertThat(TestConfig.TABLE.keySet()).containsExactly(new LoginLockId("x".repeat(20), "1.1.1.1"));
    }

}
