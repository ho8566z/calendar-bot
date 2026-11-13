package com.office.calendar.brief;

import com.office.calendar.brief.jpa.BriefLogEntity;
import com.office.calendar.brief.jpa.BriefLogRepository;
import com.office.calendar.brief.jpa.BriefResult;
import com.office.calendar.brief.mapper.BriefMapper;
import com.office.calendar.telegram.TelegramApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 주간 브리핑 실행: 대상별 처리, 결과 기록, 실패 격리
class BriefServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);
    private static final LocalDate WEEK = LocalDate.of(2026, 11, 9);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 21, 0, 0);

    private BriefMapper mapper;
    private BriefLogRepository logRepository;
    private BriefSender sender;
    private BriefService service;

    private final BriefTarget alice = new BriefTarget(1, "alice", 111L);
    private final BriefTarget bob = new BriefTarget(2, "bob", 222L);
    private final BriefTarget carol = new BriefTarget(3, "carol", 333L);

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(BriefMapper.class);
        logRepository = Mockito.mock(BriefLogRepository.class);
        sender = Mockito.mock(BriefSender.class);
        service = new BriefService(mapper, logRepository, sender, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));

        when(mapper.selectTargets(WEEK)).thenReturn(List.of());
    }

    private static BriefPlanRow row(int date, String title) {
        return new BriefPlanRow(2026, 11, date, title, null);
    }

    private void plansFor(BriefTarget t, BriefPlanRow... rows) {
        when(mapper.selectPlans(t.memberId(), 20261109, 20261115)).thenReturn(List.of(rows));
    }

    private List<BriefLogEntity> savedLogs() {
        ArgumentCaptor<BriefLogEntity> captor = ArgumentCaptor.forClass(BriefLogEntity.class);
        verify(logRepository, Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 하지 않는다")
    void noTargets() {
        BriefService.RunSummary s = service.runWeekly(SUNDAY);

        assertThat(s).isEqualTo(new BriefService.RunSummary(WEEK, 0, 0, 0, 0));
        verify(sender, never()).send(anyLong(), anyList());
        verify(logRepository, never()).save(any());
    }

    @Test
    @DisplayName("일요일에 실행하면 다음 날 월요일 주를 대상으로 조회하고, 일정은 그 주 범위(yyyymmdd)로 가져온다")
    void queriesNextWeek() {
        when(mapper.selectTargets(WEEK)).thenReturn(List.of(alice));
        plansFor(alice, row(10, "회의"));

        service.runWeekly(SUNDAY);

        verify(mapper).selectTargets(WEEK);
        verify(mapper).selectPlans("alice", 20261109, 20261115);
    }

    @Test
    @DisplayName("연도를 넘는 주는 20261228 ~ 20270103 범위로 조회한다")
    void crossYearRange() {
        LocalDate week = LocalDate.of(2026, 12, 28);
        when(mapper.selectTargets(week)).thenReturn(List.of(alice));
        when(mapper.selectPlans("alice", 20261228, 20270103)).thenReturn(List.of());

        service.runWeekly(LocalDate.of(2026, 12, 27));

        verify(mapper).selectPlans("alice", 20261228, 20270103);
    }

    @Test
    @DisplayName("일정이 있으면 발송하고 SUCCESS와 발송 시각을 기록한다")
    void success() {
        when(mapper.selectTargets(WEEK)).thenReturn(List.of(alice));
        plansFor(alice, row(10, "회의"));

        BriefService.RunSummary s = service.runWeekly(SUNDAY);

        assertThat(s).isEqualTo(new BriefService.RunSummary(WEEK, 1, 1, 0, 0));

        ArgumentCaptor<List<String>> parts = ArgumentCaptor.forClass(List.class);
        verify(sender).send(Mockito.eq(111L), parts.capture());
        assertThat(parts.getValue()).hasSize(1);
        assertThat(parts.getValue().get(0)).contains("11/10(화)", "회의");

        BriefLogEntity log = savedLogs().get(0);
        assertThat(log.getMemberNo()).isEqualTo(1);
        assertThat(log.getWeekStart()).isEqualTo(WEEK);
        assertThat(log.getResult()).isEqualTo(BriefResult.SUCCESS);
        assertThat(log.getSentAt()).isEqualTo(NOW);
        assertThat(log.getErrorMsg()).isNull();
    }

    @Test
    @DisplayName("일정이 없으면 발송하지 않고 SKIP을 기록한다(발송 시각 없음)")
    void skipWhenNoPlans() {
        when(mapper.selectTargets(WEEK)).thenReturn(List.of(alice));
        plansFor(alice);

        BriefService.RunSummary s = service.runWeekly(SUNDAY);

        assertThat(s).isEqualTo(new BriefService.RunSummary(WEEK, 1, 0, 0, 1));
        verify(sender, never()).send(anyLong(), anyList());
        BriefLogEntity log = savedLogs().get(0);
        assertThat(log.getResult()).isEqualTo(BriefResult.SKIP);
        assertThat(log.getSentAt()).isNull();
    }

    @Test
    @DisplayName("일정이 모두 달력에 없는 날짜(2/31)뿐이어도 SKIP")
    void skipWhenOnlyInvalidDates() {
        when(mapper.selectTargets(WEEK)).thenReturn(List.of(alice));
        plansFor(alice, new BriefPlanRow(2026, 11, 31, "없는 날짜", null));

        assertThat(service.runWeekly(SUNDAY).skipped()).isEqualTo(1);
        verify(sender, never()).send(anyLong(), anyList());
    }

    @Test
    @DisplayName("봇 차단(403)은 FAIL로 기록하고 다음 사용자는 계속 처리한다")
    void blockedDoesNotStopOthers() {
        when(mapper.selectTargets(WEEK)).thenReturn(List.of(alice, bob, carol));
        plansFor(alice, row(10, "a"));
        plansFor(bob, row(10, "b"));
        plansFor(carol, row(10, "c"));
        doThrow(new TelegramApiException(403, 0)).when(sender).send(Mockito.eq(222L), anyList());

        BriefService.RunSummary s = service.runWeekly(SUNDAY);

        assertThat(s).isEqualTo(new BriefService.RunSummary(WEEK, 3, 2, 1, 0));
        verify(sender).send(Mockito.eq(111L), anyList());
        verify(sender).send(Mockito.eq(333L), anyList());

        List<BriefLogEntity> logs = savedLogs();
        assertThat(logs).extracting(BriefLogEntity::getResult)
                .containsExactly(BriefResult.SUCCESS, BriefResult.FAIL, BriefResult.SUCCESS);
        BriefLogEntity failed = logs.get(1);
        assertThat(failed.getMemberNo()).isEqualTo(2);
        assertThat(failed.getErrorMsg()).isEqualTo("봇 차단 (HTTP 403)");
        assertThat(failed.getSentAt()).isNull();
    }

    @Test
    @DisplayName("네트워크 오류·429는 상태를 담은 안전한 문구로 FAIL 기록")
    void networkErrorRecorded() {
        when(mapper.selectTargets(WEEK)).thenReturn(List.of(alice, bob));
        plansFor(alice, row(10, "a"));
        plansFor(bob, row(10, "b"));
        doThrow(new TelegramApiException(TelegramApiException.NETWORK_ERROR, 0)).when(sender).send(Mockito.eq(111L), anyList());
        doThrow(new TelegramApiException(429, 60)).when(sender).send(Mockito.eq(222L), anyList());

        service.runWeekly(SUNDAY);

        List<BriefLogEntity> logs = savedLogs();
        assertThat(logs.get(0).getErrorMsg()).isEqualTo("텔레그램 요청 실패(연결 오류)");
        assertThat(logs.get(1).getErrorMsg()).isEqualTo("텔레그램 요청 실패(HTTP 429)");
    }

    @Test
    @DisplayName("예상 못 한 예외도 FAIL로 기록하고, 예외 메시지(일정 제목·chat ID가 섞일 수 있음)는 기록에 넣지 않는다")
    void unexpectedExceptionRecordedWithoutMessage() {
        when(mapper.selectTargets(WEEK)).thenReturn(List.of(alice, bob));
        plansFor(alice, row(10, "비밀 일정"));
        plansFor(bob, row(10, "b"));
        doThrow(new IllegalStateException("비밀 일정 chat=111")).when(sender).send(Mockito.eq(111L), anyList());

        BriefService.RunSummary s = service.runWeekly(SUNDAY);

        assertThat(s.failed()).isEqualTo(1);
        assertThat(s.sent()).isEqualTo(1);
        BriefLogEntity failed = savedLogs().get(0);
        assertThat(failed.getResult()).isEqualTo(BriefResult.FAIL);
        assertThat(failed.getErrorMsg()).isEqualTo("처리 중 오류(IllegalStateException)")
                .doesNotContain("비밀").doesNotContain("111");
    }

    @Test
    @DisplayName("일정 조회가 실패한 사용자는 FAIL로 기록하고 다음 사용자를 계속 처리한다")
    void planQueryFailureIsolated() {
        when(mapper.selectTargets(WEEK)).thenReturn(List.of(alice, bob));
        when(mapper.selectPlans("alice", 20261109, 20261115)).thenThrow(new IllegalStateException("db"));
        plansFor(bob, row(10, "b"));

        BriefService.RunSummary s = service.runWeekly(SUNDAY);

        assertThat(s).isEqualTo(new BriefService.RunSummary(WEEK, 2, 1, 1, 0));
        verify(sender, never()).send(Mockito.eq(111L), anyList());
    }

    @Test
    @DisplayName("기록 저장이 실패해도 다음 사용자 발송은 계속된다")
    void logFailureDoesNotStopOthers() {
        when(mapper.selectTargets(WEEK)).thenReturn(List.of(alice, bob));
        plansFor(alice, row(10, "a"));
        plansFor(bob, row(10, "b"));
        doThrow(new IllegalStateException("log table down")).doReturn(null).when(logRepository).save(any());

        BriefService.RunSummary s = service.runWeekly(SUNDAY);

        assertThat(s.sent()).isEqualTo(2);
        verify(sender).send(Mockito.eq(222L), anyList());
        verify(logRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("같은 (회원, 주) 기록이 이미 있어 유니크 위반이 나도 오류 없이 넘어간다")
    void duplicateLogIgnored() {
        when(mapper.selectTargets(WEEK)).thenReturn(List.of(alice));
        plansFor(alice, row(10, "a"));
        doThrow(new DataIntegrityViolationException("dup")).when(logRepository).save(any());

        BriefService.RunSummary s = service.runWeekly(SUNDAY);

        assertThat(s.sent()).isEqualTo(1);
    }

    @Test
    @DisplayName("오류 사유는 255자를 넘지 않는다")
    void errorMessageTruncated() {
        when(mapper.selectTargets(WEEK)).thenReturn(List.of(alice));
        plansFor(alice, row(10, "a"));
        doThrow(new TelegramApiException(500, 0) {
            @Override
            public String getMessage() {
                return "x".repeat(400);
            }
        }).when(sender).send(anyLong(), anyList());

        service.runWeekly(SUNDAY);

        assertThat(savedLogs().get(0).getErrorMsg()).hasSize(255);
    }

    @Test
    @DisplayName("날짜 키는 yyyymmdd 정수")
    void dateKey() {
        assertThat(BriefService.dateKey(LocalDate.of(2026, 11, 9))).isEqualTo(20261109);
        assertThat(BriefService.dateKey(LocalDate.of(2027, 1, 3))).isEqualTo(20270103);
    }

}
