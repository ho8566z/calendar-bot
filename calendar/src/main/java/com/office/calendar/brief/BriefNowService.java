package com.office.calendar.brief;

import com.office.calendar.member.jpa.MemberEntity;
import com.office.calendar.member.jpa.MemberRepository;
import com.office.calendar.brief.mapper.BriefMapper;
import com.office.calendar.telegram.TelegramApiException;
import com.office.calendar.telegram.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// "지금 보내기": 로그인한 본인에게 다음 주 브리핑을 미리 보내 준다(시연·확인용).
// 정기 발송과 달리 BRIEF_LOG에 기록하지 않아 같은 주에도 여러 번 보낼 수 있고, 일정이 없으면 없다고 알려 준다.
// 텔레그램 호출을 남용하지 못하도록 같은 회원은 일정 간격 안에 다시 보낼 수 없다.
@Slf4j
@Service
@RequiredArgsConstructor
public class BriefNowService {

    // 같은 회원이 다시 보내려면 기다려야 하는 시간
    static final Duration COOLDOWN = Duration.ofSeconds(10);

    public enum Outcome { SENT, NOT_CONFIGURED, NOT_LINKED, TOO_FREQUENT, SEND_FAILED }

    // 결과. SENT면 weekStart·empty가 의미 있고, TOO_FREQUENT면 retryAfterSeconds, SEND_FAILED면 blocked 여부가 의미 있다
    public record Result(Outcome outcome, LocalDate weekStart, boolean empty, int retryAfterSeconds, boolean blocked) {

        static Result of(Outcome outcome) {
            return new Result(outcome, null, false, 0, false);
        }

    }

    final private MemberRepository memberRepository;
    final private BriefMapper briefMapper;
    final private BriefSender briefSender;
    final private TelegramProperties telegramProperties;
    final private Clock clock;

    // 회원 번호별 마지막으로 보낸 시각
    private final Map<Integer, Instant> lastSent = new ConcurrentHashMap<>();

    public Result sendNow(String memberId) {
        log.info("sendNow()");

        if (!telegramProperties.hasValidToken()) {
            return Result.of(Outcome.NOT_CONFIGURED);
        }

        MemberEntity member = memberRepository.findByMemId(memberId)
                .orElseThrow(() -> new IllegalStateException("로그인한 회원을 찾을 수 없다"));
        if (member.getTelegramChatId() == null) {
            return Result.of(Outcome.NOT_LINKED);
        }

        Instant now = clock.instant();
        Instant previous = lastSent.get(member.getMemNo());
        if (previous != null && now.isBefore(previous.plus(COOLDOWN))) {
            long wait = Duration.between(now, previous.plus(COOLDOWN)).toSeconds() + 1;
            return new Result(Outcome.TOO_FREQUENT, null, false, (int) wait, false);
        }
        lastSent.put(member.getMemNo(), now);       // 실패해도 간격은 지킨다(연타로 텔레그램에 계속 요청하지 않게)

        LocalDate weekStart = BriefWeek.nextWeekStart(LocalDate.now(clock));
        LocalDate weekEnd = BriefWeek.weekEnd(weekStart);
        List<BriefPlanRow> plans = briefMapper.selectPlans(memberId, BriefService.dateKey(weekStart), BriefService.dateKey(weekEnd));
        List<String> parts = BriefMessageBuilder.build(weekStart, plans);

        boolean empty = parts.isEmpty();
        if (empty) {
            parts = List.of("다음 주(" + BriefMessageBuilder.shortDate(weekStart) + " ~ " + BriefMessageBuilder.shortDate(weekEnd)
                    + ")에는 등록된 일정이 없습니다.");
        }

        try {
            briefSender.send(member.getTelegramChatId(), parts);

        } catch (TelegramApiException e) {
            log.warn("지금 보내기 실패: {}", e.getMessage());
            return new Result(Outcome.SEND_FAILED, null, false, 0, e.isBlocked());
        }

        return new Result(Outcome.SENT, weekStart, empty, 0, false);

    }

}
