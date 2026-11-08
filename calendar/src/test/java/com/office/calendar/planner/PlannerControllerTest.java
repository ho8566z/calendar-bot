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
