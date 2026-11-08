package com.office.calendar.brief;

import com.office.calendar.telegram.TelegramProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

// 정해진 시각에 주간 브리핑을 보낸다(brf-002). 기본은 매주 일요일 21:00(KST)이고 다음 주(월~일) 일정을 보낸다.
// 시각은 brief.cron 으로 바꿀 수 있고, brief.scheduler.enabled=false 로 끌 수 있다(여러 서버에서 띄울 때 한 곳에서만 켠다).
@Slf4j
@Component
public class BriefScheduler {

    private final BriefService briefService;
    private final TelegramProperties telegramProperties;
    private final Clock clock;
    private final boolean enabled;

    public BriefScheduler(BriefService briefService, TelegramProperties telegramProperties, Clock clock,
                          @Value("${brief.scheduler.enabled:true}") boolean enabled) {
        this.briefService = briefService;
        this.telegramProperties = telegramProperties;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${brief.cron:0 0 21 * * SUN}", zone = "Asia/Seoul")
    public void sendWeeklyBrief() {
        if (!enabled) {
            return;
        }
        if (!telegramProperties.isConfigured()) {
            log.warn("텔레그램 봇이 설정되지 않아 주간 브리핑을 건너뛴다");
            return;
        }

        try {
            briefService.runWeekly(LocalDate.now(clock));

        } catch (RuntimeException e) {
            // 한 번 실패해도 다음 주 실행은 계속되어야 한다. 예외 메시지는 남기지 않고 종류만 남긴다
            log.error("주간 브리핑 실행 중 오류: {}", e.getClass().getSimpleName());
        }

    }

}
