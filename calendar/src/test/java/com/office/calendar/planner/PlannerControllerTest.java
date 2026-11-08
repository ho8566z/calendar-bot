package com.office.calendar.planner;

import com.office.calendar.config.SecurityConfig;
import com.office.calendar.planner.util.UploadFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 일정 등록·수정의 입력 처리: 클라이언트가 보낸 번호·소유자·이미지 이름은 무시하고 허용한 항목만 쓴다
@SpringJUnitWebConfig(classes = {SecurityConfig.class, PlannerControllerTest.WebConfig.class})
class PlannerControllerTest {

    @Configuration
    @EnableWebMvc
    static class WebConfig {

        @Bean
        PlannerService plannerService() {
            return Mockito.mock(PlannerService.class);
        }

        @Bean
        UploadFileService uploadFileService() {
            return Mockito.mock(UploadFileService.class);
        }

        @Bean
        PlannerController plannerController(PlannerService plannerService, UploadFileService uploadFileService) {
            return new PlannerController(plannerService, uploadFileService);
        }

    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PlannerService plannerService;
    @Autowired
    private UploadFileService uploadFileService;

    private MockMvc mockMvc;

    private final MockMultipartFile image =
            new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        Mockito.reset(plannerService, uploadFileService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("등록: 클라이언트가 번호·소유자·이미지 이름·날짜를 보내도 무시하고 로그인한 사용자 기준으로 저장한다")
    void write_ignoresClientSuppliedFields() throws Exception {
        when(uploadFileService.upload("alice", image)).thenReturn("saved.png");
        when(plannerService.writePlan(any())).thenReturn(Map.of("result", 1));

        mockMvc.perform(multipart("/planner/plan").file(image)
                        .param("year", "2026").param("month", "11").param("date", "8")
                        .param("title", "제목").param("body", "내용")
                        // 남의 일정 번호를 넣어 덮어쓰려는 시도와 그 밖의 위조 값
                        .param("no", "999").param("ori_no", "999")
                        .param("owner_id", "victim").param("ori_owner_id", "victim")
                        .param("img_name", "../evil.png").param("reg_date", "2000-01-01 00:00:00")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(1));

        ArgumentCaptor<PlannerDto> captor = ArgumentCaptor.forClass(PlannerDto.class);
        verify(plannerService).writePlan(captor.capture());
        PlannerDto saved = captor.getValue();
        assertThat(saved.getNo()).as("새 일정이므로 번호는 0").isEqualTo(0);
        assertThat(saved.getOri_no()).isEqualTo(0);
        assertThat(saved.getOwner_id()).as("소유자는 로그인한 사용자").isEqualTo("alice");
        assertThat(saved.getOri_owner_id()).isNull();
        assertThat(saved.getImg_name()).as("이미지 이름은 서버가 저장한 것").isEqualTo("saved.png");
        assertThat(saved.getReg_date()).isNull();
        assertThat(saved.getYear()).isEqualTo(2026);
        assertThat(saved.getTitle()).isEqualTo("제목");
        assertThat(saved.getBody()).isEqualTo("내용");
    }

    @Test
    @DisplayName("등록: 이미지를 고르지 않아도 등록되고 이미지 이름은 비어 있다(plan-006)")
    void write_withoutImage() throws Exception {
        when(plannerService.writePlan(any())).thenReturn(Map.of("result", 1));

        mockMvc.perform(multipart("/planner/plan")
                        .param("year", "2026").param("month", "11").param("date", "8")
                        .param("title", "이미지 없음").param("body", "내용")
                        .param("img_name", "fake.png")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(1));

        ArgumentCaptor<PlannerDto> captor = ArgumentCaptor.forClass(PlannerDto.class);
        verify(plannerService).writePlan(captor.capture());
        assertThat(captor.getValue().getImg_name()).as("파일이 없으면 클라이언트가 보낸 이름도 쓰지 않는다").isNull();
        assertThat(captor.getValue().getOwner_id()).isEqualTo("alice");
        verify(uploadFileService, never()).upload(anyString(), any());
    }

    @Test
    @DisplayName("등록: 빈 파일(0바이트)은 고르지 않은 것으로 보고 이미지 없이 등록한다")
    void write_emptyFile() throws Exception {
        when(plannerService.writePlan(any())).thenReturn(Map.of("result", 1));
        MockMultipartFile empty = new MockMultipartFile("file", "", "application/octet-stream", new byte[0]);

        mockMvc.perform(multipart("/planner/plan").file(empty)
                        .param("year", "2026").param("month", "11").param("date", "8")
                        .param("title", "t").param("body", "b")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk());

        verify(uploadFileService, never()).upload(anyString(), any());
        verify(plannerService).writePlan(any());
    }

    @Test
    @DisplayName("수정: 빈 파일이 같이 와도 기존 이미지를 유지한 채 수정된다")
    void modify_emptyFile() throws Exception {
        when(plannerService.isOwner(5, "alice")).thenReturn(true);
        when(plannerService.modifyPlan(any(), anyString())).thenReturn(Map.of("result", 1));
        MockMultipartFile empty = new MockMultipartFile("file", "", "application/octet-stream", new byte[0]);

        mockMvc.perform(multipart(HttpMethod.PUT, "/planner/plan/5").file(empty)
                        .param("year", "2026").param("month", "11").param("date", "9")
                        .param("title", "t").param("body", "b")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk());

        ArgumentCaptor<PlannerDto> captor = ArgumentCaptor.forClass(PlannerDto.class);
        verify(plannerService).modifyPlan(captor.capture(), org.mockito.ArgumentMatchers.eq("alice"));
        assertThat(captor.getValue().getImg_name()).as("이미지 이름이 비어 있으면 서비스가 기존 이미지를 유지한다").isNull();
        verify(uploadFileService, never()).upload(anyString(), any());
    }

    @Test
    @DisplayName("검색: 로그인한 사람의 아이디로만 검색한다(요청 값으로 다른 사람을 지정할 수 없다)")
    void search_usesPrincipal() throws Exception {
        when(plannerService.searchPlans("치과", "alice")).thenReturn(Map.of("plans", java.util.List.of(), "limit", 100));

        mockMvc.perform(get("/planner/plans/search").param("keyword", "치과").param("owner_id", "bob")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(100));

        verify(plannerService).searchPlans("치과", "alice");
        verify(plannerService, never()).searchPlans(anyString(), org.mockito.ArgumentMatchers.eq("bob"));
    }

    @Test
    @DisplayName("통계: 로그인한 사람의 아이디로 조회한다")
    void stats_usesPrincipal() throws Exception {
        when(plannerService.getMonthlyStats(2026, 11, "alice")).thenReturn(Map.of("total", 3));

        mockMvc.perform(get("/planner/stats/monthly").param("year", "2026").param("month", "11").param("owner_id", "bob")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));

        verify(plannerService).getMonthlyStats(2026, 11, "alice");
    }

    @Test
    @DisplayName("통계: 범위(2025.1 ~ 2030.12) 밖이면 400 INVALID_PERIOD이고 조회하지 않는다")
    void stats_outOfRange() throws Exception {
        String[][] bad = {{"2024", "12"}, {"2031", "1"}, {"2026", "0"}, {"2026", "13"}};
        for (String[] ym : bad) {
            mockMvc.perform(get("/planner/stats/monthly").param("year", ym[0]).param("month", ym[1])
                            .with(user("alice").roles("USER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        }

        verify(plannerService, never()).getMonthlyStats(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), anyString());
    }

    @Test
    @DisplayName("통계: year나 month가 빠지거나 숫자가 아니면 400")
    void stats_badParams() throws Exception {
        mockMvc.perform(get("/planner/stats/monthly").param("year", "2026").with(user("alice").roles("USER")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/planner/stats/monthly").param("year", "abc").param("month", "1").with(user("alice").roles("USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("검색: keyword 없이 호출해도 400이 아니라 200(빈 목록)")
    void search_withoutKeyword() throws Exception {
        when(plannerService.searchPlans(null, "alice")).thenReturn(Map.of("plans", java.util.List.of(), "limit", 100));

        mockMvc.perform(get("/planner/plans/search").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans").isEmpty());
    }

    @Test
    @DisplayName("등록: 시작 시간(HH:mm)을 보내면 그 값으로 저장한다")
    void write_withStartTime() throws Exception {
        when(plannerService.writePlan(any())).thenReturn(Map.of("result", 1));

        mockMvc.perform(multipart("/planner/plan")
                        .param("year", "2026").param("month", "11").param("date", "8")
                        .param("title", "t").param("body", "b").param("start_time", "14:30")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk());

        ArgumentCaptor<PlannerDto> captor = ArgumentCaptor.forClass(PlannerDto.class);
        verify(plannerService).writePlan(captor.capture());
        assertThat(captor.getValue().getStart_time()).isEqualTo("14:30");
    }

    @Test
    @DisplayName("등록: 시작 시간을 비워 보내면 시간 없음으로 저장한다")
    void write_blankStartTime() throws Exception {
        when(plannerService.writePlan(any())).thenReturn(Map.of("result", 1));

        mockMvc.perform(multipart("/planner/plan")
                        .param("year", "2026").param("month", "11").param("date", "8")
                        .param("title", "t").param("body", "b").param("start_time", "")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk());

        ArgumentCaptor<PlannerDto> captor = ArgumentCaptor.forClass(PlannerDto.class);
        verify(plannerService).writePlan(captor.capture());
        assertThat(captor.getValue().getStart_time()).isNull();
    }

    @Test
    @DisplayName("등록: 시작 시간 형식이 틀리면 400 INVALID_START_TIME이고 파일도 일정도 저장하지 않는다")
    void write_invalidStartTime() throws Exception {
        mockMvc.perform(multipart("/planner/plan").file(image)
                        .param("year", "2026").param("month", "11").param("date", "8")
                        .param("title", "t").param("body", "b").param("start_time", "25:00")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_START_TIME"));

        verify(uploadFileService, never()).upload(anyString(), any());
        verify(plannerService, never()).writePlan(any());
    }

    @Test
    @DisplayName("수정: 시작 시간을 비워 보내면 null로 전달해 시간이 지워지게 한다")
    void modify_blankStartTime() throws Exception {
        when(plannerService.isOwner(5, "alice")).thenReturn(true);
        when(plannerService.modifyPlan(any(), anyString())).thenReturn(Map.of("result", 1));

        mockMvc.perform(multipart(HttpMethod.PUT, "/planner/plan/5")
                        .param("year", "2026").param("month", "11").param("date", "9")
                        .param("title", "t").param("body", "b").param("start_time", "")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk());

        ArgumentCaptor<PlannerDto> captor = ArgumentCaptor.forClass(PlannerDto.class);
        verify(plannerService).modifyPlan(captor.capture(), org.mockito.ArgumentMatchers.eq("alice"));
        assertThat(captor.getValue().getStart_time()).isNull();
    }

    @Test
    @DisplayName("수정: 시작 시간 형식이 틀리면 400이고 파일도 수정도 하지 않는다")
    void modify_invalidStartTime() throws Exception {
        when(plannerService.isOwner(5, "alice")).thenReturn(true);

        mockMvc.perform(multipart(HttpMethod.PUT, "/planner/plan/5").file(image)
                        .param("year", "2026").param("month", "11").param("date", "9")
                        .param("title", "t").param("body", "b").param("start_time", "9:30")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_START_TIME"));

        verify(uploadFileService, never()).upload(anyString(), any());
        verify(plannerService, never()).modifyPlan(any(), anyString());
    }

    @Test
    @DisplayName("수정: 남의 일정이면 시작 시간이 틀려도 형식 오류가 아니라 404(존재 여부를 알리지 않음)")
    void modify_notOwnerBeatsInvalidStartTime() throws Exception {
        when(plannerService.isOwner(5, "mallory")).thenReturn(false);

        mockMvc.perform(multipart(HttpMethod.PUT, "/planner/plan/5")
                        .param("year", "2026").param("month", "11").param("date", "9")
                        .param("title", "t").param("body", "b").param("start_time", "99:99")
                        .with(user("mallory").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("등록: 파일 저장에 실패하면 400이고 일정은 저장하지 않는다")
    void write_uploadFails() throws Exception {
        when(uploadFileService.upload(anyString(), any())).thenReturn(null);

        mockMvc.perform(multipart("/planner/plan").file(image)
                        .param("year", "2026").param("month", "11").param("date", "8")
                        .param("title", "t").param("body", "b")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isBadRequest());

        verify(plannerService, never()).writePlan(any());
    }

    @Test
    @DisplayName("수정: 클라이언트가 보낸 이미지 이름은 무시하고 번호는 경로의 값을 쓴다")
    void modify_ignoresClientSuppliedFields() throws Exception {
        when(plannerService.isOwner(5, "alice")).thenReturn(true);
        when(plannerService.modifyPlan(any(), anyString())).thenReturn(Map.of("result", 1));

        mockMvc.perform(multipart(HttpMethod.PUT, "/planner/plan/5")
                        .param("year", "2026").param("month", "11").param("date", "9")
                        .param("title", "새 제목").param("body", "새 내용")
                        .param("no", "777").param("img_name", "../evil.png").param("owner_id", "victim")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk());

        ArgumentCaptor<PlannerDto> captor = ArgumentCaptor.forClass(PlannerDto.class);
        verify(plannerService).modifyPlan(captor.capture(), org.mockito.ArgumentMatchers.eq("alice"));
        PlannerDto changed = captor.getValue();
        assertThat(changed.getNo()).as("번호는 경로의 5").isEqualTo(5);
        assertThat(changed.getImg_name()).as("파일을 올리지 않았으면 이미지 이름 없음").isNull();
        assertThat(changed.getOwner_id()).isNull();
        assertThat(changed.getTitle()).isEqualTo("새 제목");
        verify(uploadFileService, never()).upload(anyString(), any());
    }

    @Test
    @DisplayName("수정: 남의 일정이면 파일을 저장하기 전에 404로 끝낸다")
    void modify_notOwner() throws Exception {
        when(plannerService.isOwner(5, "mallory")).thenReturn(false);

        mockMvc.perform(multipart(HttpMethod.PUT, "/planner/plan/5").file(image)
                        .param("year", "2026").param("month", "11").param("date", "9")
                        .param("title", "x").param("body", "x")
                        .with(user("mallory").roles("USER")))
                .andExpect(status().isNotFound());

        verify(uploadFileService, never()).upload(anyString(), any());
        verify(plannerService, never()).modifyPlan(any(), anyString());
    }

    @Test
    @DisplayName("수정: 새 파일을 올리면 서버가 저장한 이름을 쓴다")
    void modify_withFile() throws Exception {
        when(plannerService.isOwner(5, "alice")).thenReturn(true);
        when(uploadFileService.upload("alice", image)).thenReturn("new.png");
        when(plannerService.modifyPlan(any(), anyString())).thenReturn(Map.of("result", 1));

        mockMvc.perform(multipart(HttpMethod.PUT, "/planner/plan/5").file(image)
                        .param("year", "2026").param("month", "11").param("date", "9")
                        .param("title", "t").param("body", "b")
                        .with(user("alice").roles("USER")))
                .andExpect(status().isOk());

        ArgumentCaptor<PlannerDto> captor = ArgumentCaptor.forClass(PlannerDto.class);
        verify(plannerService).modifyPlan(captor.capture(), org.mockito.ArgumentMatchers.eq("alice"));
        assertThat(captor.getValue().getImg_name()).isEqualTo("new.png");
    }

}
