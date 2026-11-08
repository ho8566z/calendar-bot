package com.office.calendar.brief;

import com.office.calendar.telegram.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 정기 발송 스케줄러: 켜짐·설정 여부, 예외 격리, 실행 시각 설정
class BriefSchedulerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final TelegramProperties CONFIGURED = new TelegramProperties("tok", "MyCalBot", 10);

    private BriefService briefService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        briefService = Mockito.mock(BriefService.class);
        clock = Clock.fixed(LocalDateTime.of(2026, 11, 8, 21, 0).atZone(ZONE).toInstant(), ZONE);
    }

    @Test
    @DisplayName("켜져 있고 봇이 설정돼 있으면 오늘 날짜(KST) 기준으로 실행한다")
    void runs() {
        new BriefScheduler(briefService, CONFIGURED, clock, true).sendWeeklyBrief();

        verify(briefService).runWeekly(LocalDate.of(2026, 11, 8));
    }

    @Test
    @DisplayName("꺼져 있으면 실행하지 않는다")
    void disabled() {
        new BriefScheduler(briefService, CONFIGURED, clock, false).sendWeeklyBrief();

        verify(briefService, never()).runWeekly(Mockito.any());
    }

    @Test
    @DisplayName("봇이 설정되지 않았으면 실행하지 않는다")
    void notConfigured() {
        new BriefScheduler(briefService, new TelegramProperties("", "", 10), clock, true).sendWeeklyBrief();

        verify(briefService, never()).runWeekly(Mockito.any());
    }

    @Test
    @DisplayName("실행 중 예외가 나도 밖으로 던지지 않는다(다음 주 실행이 이어져야 함)")
    void exceptionSwallowed() {
        when(briefService.runWeekly(Mockito.any())).thenThrow(new IllegalStateException("db down"));

        new BriefScheduler(briefService, CONFIGURED, clock, true).sendWeeklyBrief();

        verify(briefService).runWeekly(LocalDate.of(2026, 11, 8));
    }

    @Test
    @DisplayName("기본 실행 시각은 매주 일요일 21:00 KST이고 시각 설정(brief.cron)으로 바꿀 수 있다")
    void cronDefault() throws Exception {
        Scheduled scheduled = BriefScheduler.class.getMethod("sendWeeklyBrief").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${brief.cron:0 0 21 * * SUN}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");

        CronExpression cron = CronExpression.parse("0 0 21 * * SUN");
        assertThat(cron.next(LocalDateTime.of(2026, 11, 8, 20, 59))).isEqualTo(LocalDateTime.of(2026, 11, 8, 21, 0));
        assertThat(cron.next(LocalDateTime.of(2026, 11, 8, 21, 0))).isEqualTo(LocalDateTime.of(2026, 11, 15, 21, 0));
        assertThat(cron.next(LocalDateTime.of(2026, 11, 9, 8, 0))).isEqualTo(LocalDateTime.of(2026, 11, 15, 21, 0));
    }

}
