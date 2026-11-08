package com.office.calendar.brief;

import com.office.calendar.brief.jpa.BriefLogEntity;
import com.office.calendar.brief.jpa.BriefLogRepository;
import com.office.calendar.brief.jpa.BriefResult;
import com.office.calendar.brief.mapper.BriefMapper;
import com.office.calendar.telegram.TelegramApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 주간 브리핑 대상 선정 → 목록 생성 → 발송 → 결과 기록(brf-001, 002, 004).
// 한 사용자의 실패(봇 차단, 네트워크 오류, 기록 저장 실패)가 다른 사용자 발송을 막지 않는다.
// 사용자마다 DB 저장이 따로 끝나도록 이 클래스 전체를 하나의 트랜잭션으로 묶지 않는다.
@Slf4j
@Service
@RequiredArgsConstructor
public class BriefService {

    // 한 번 실행한 결과 요약
    public record RunSummary(LocalDate weekStart, int targets, int sent, int failed, int skipped) {
    }

    final private BriefMapper briefMapper;
    final private BriefLogRepository briefLogRepository;
    final private BriefSender briefSender;
    final private Clock clock;

    // today 기준 다음 주(월~일) 브리핑을 대상 전원에게 보낸다
    public RunSummary runWeekly(LocalDate today) {
        LocalDate weekStart = BriefWeek.nextWeekStart(today);
        log.info("주간 브리핑을 시작한다: 대상 주 {}", weekStart);

        List<BriefTarget> targets = briefMapper.selectTargets(weekStart);

        int sent = 0;
        int failed = 0;
        int skipped = 0;
        for (BriefTarget target : targets) {
            BriefResult result = processOne(target, weekStart);
            switch (result) {
                case SUCCESS -> sent++;
                case SKIP -> skipped++;
                default -> failed++;
            }
        }

        log.info("주간 브리핑을 마쳤다: 대상 {}명, 발송 {}, 실패 {}, 일정 없음 {}", targets.size(), sent, failed, skipped);
        return new RunSummary(weekStart, targets.size(), sent, failed, skipped);

    }

    // 한 사용자 처리. 어떤 예외도 밖으로 내보내지 않는다
    private BriefResult processOne(BriefTarget target, LocalDate weekStart) {
        BriefResult result;
        String error = null;

        try {
            List<BriefPlanRow> plans = briefMapper.selectPlans(target.getMemberId(), dateKey(weekStart), dateKey(BriefWeek.weekEnd(weekStart)));
            List<String> parts = BriefMessageBuilder.build(weekStart, plans);

            if (parts.isEmpty()) {
                result = BriefResult.SKIP;

            } else {
                briefSender.send(target.getChatId(), parts);
                result = BriefResult.SUCCESS;
            }

        } catch (TelegramApiException e) {
            result = BriefResult.FAIL;
            error = e.isBlocked() ? "봇 차단 (HTTP 403)" : e.getMessage();

        } catch (RuntimeException e) {
            // 일정 제목·chat ID가 섞일 수 있어 예외 메시지는 쓰지 않고 종류만 남긴다
            result = BriefResult.FAIL;
            error = "처리 중 오류(" + e.getClass().getSimpleName() + ")";
        }

        record(target, weekStart, result, error);
        return result;

    }

    // 결과를 BRIEF_LOG에 남긴다. 같은 (회원, 주)가 이미 있으면(동시 실행) 무시하고, 저장이 실패해도 다음 사용자로 진행한다
    private void record(BriefTarget target, LocalDate weekStart, BriefResult result, String error) {
        try {
            briefLogRepository.save(BriefLogEntity.builder()
                    .memberNo(target.getMemberNo())
                    .weekStart(weekStart)
                    .sentAt(result == BriefResult.SUCCESS ? LocalDateTime.now(clock) : null)
                    .result(result)
                    .errorMsg(truncate(error))
                    .build());

        } catch (DataIntegrityViolationException e) {
            log.info("이미 기록된 브리핑이라 건너뛴다");

        } catch (RuntimeException e) {
            log.warn("브리핑 결과를 기록하지 못했다: {}", e.getClass().getSimpleName());
        }

    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > BriefLogEntity.ERROR_MSG_MAX ? text.substring(0, BriefLogEntity.ERROR_MSG_MAX) : text;
    }

    // yyyymmdd 정수(예: 2026-11-09 → 20261109)
    static int dateKey(LocalDate d) {
        return d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
    }

}
