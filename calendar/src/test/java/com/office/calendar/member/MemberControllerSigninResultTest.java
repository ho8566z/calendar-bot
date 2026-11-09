package com.office.calendar.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// 로그인 결과 화면에 넘기는 값: 잠금 안내(locked)와 잠금 시간(lockMinutes)
class MemberControllerSigninResultTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MemberController(Mockito.mock(MemberService.class), 15))
            .build();

    @Test
    @DisplayName("locked=true 로 오면 잠금 안내를 켜고 설정된 분을 넘긴다")
    void locked() throws Exception {
        mockMvc.perform(get("/member/signin_result").param("locked", "true"))
                .andExpect(view().name("member/signin_result"))
                .andExpect(model().attribute("locked", true))
                .andExpect(model().attribute("lockMinutes", 15));
    }

    @Test
    @DisplayName("locked 가 없거나 true 가 아니면 안내를 켜지 않는다")
    void notLocked() throws Exception {
        mockMvc.perform(get("/member/signin_result"))
                .andExpect(model().attribute("locked", false));
        mockMvc.perform(get("/member/signin_result").param("locked", "yes"))
                .andExpect(model().attribute("locked", false));
    }

    @Test
    @DisplayName("로그인 성공 주소(loginedID)에는 locked 가 붙어도 안내를 켜지 않는다")
    void successWins() throws Exception {
        mockMvc.perform(get("/member/signin_result").param("loginedID", "alice").param("locked", "true"))
                .andExpect(model().attribute("locked", false));
    }

}
