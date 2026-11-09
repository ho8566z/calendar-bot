package com.office.calendar.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 봇이 받은 '/start 연동코드' 메시지를 getUpdates 롱 폴링으로 가져와 계정과 chat ID를 연결한다(brf-005).
// 웹훅은 공개 HTTPS 주소가 필요해 쓰지 않는다. 폴링은 서버 한 곳에서만 해야 한다(같은 봇을 두 곳에서 폴링하면 409).
// 연동 코드·chat ID·메시지 본문과 요청 주소(토큰 포함)는 로그에 남기지 않는다.
@Slf4j
@Component
public class TelegramPoller {

    static final int POLL_TIMEOUT_SECONDS = 25;
    static final Duration ERROR_PAUSE = Duration.ofSeconds(10);
    static final Duration CONFLICT_PAUSE = Duration.ofSeconds(30);
    static final Duration PURGE_INTERVAL = Duration.ofMinutes(5);

    static final String REPLY_LINKED = "텔레그램이 연동되었습니다. 앱의 내 정보 화면에서 주간 브리핑 수신을 켜면 매주 일정을 보내 드려요.";
    static final String REPLY_INVALID = "연동 코드가 올바르지 않거나 만료되었습니다. 앱의 내 정보 화면에서 새 코드를 발급해 주세요.";
    static final String REPLY_CHAT_IN_USE = "이 텔레그램은 이미 다른 계정에 연결되어 있습니다.";
    static final String REPLY_MEMBER_LINKED = "이 계정은 이미 텔레그램이 연동되어 있습니다.";
    static final String REPLY_USAGE = "앱의 내 정보 화면에서 연동 코드를 발급받아, 안내된 링크로 들어와 시작 버튼을 눌러 주세요.";

    // '/start' 또는 '/start@봇이름', 뒤에 코드가 올 수 있다
    private static final Pattern START = Pattern.compile("^/start(?:@\\w+)?(?:\\s+(.*))?$", Pattern.DOTALL);

    private final TelegramApi telegramApi;
    private final TelegramLinkService telegramLinkService;
    private final TelegramProperties properties;
    private final Clock clock;
    private final boolean enabled;

    // 다음에 요청할 offset(처리한 update_id + 1). null이면 시작 동기화 전
    private Long offset = null;
    private Instant pausedUntil = Instant.MIN;
    private Instant lastPurge = Instant.MIN;

    public TelegramPoller(TelegramApi telegramApi, TelegramLinkService telegramLinkService, TelegramProperties properties,
                          Clock clock, @Value("${telegram.polling.enabled:true}") boolean enabled) {
        this.telegramApi = telegramApi;
        this.telegramLinkService = telegramLinkService;
        this.properties = properties;
        this.clock = clock;
        this.enabled = enabled;
    }

    // 이전 폴링이 끝난 1초 뒤에 다시 부른다(롱 폴링이라 한 번에 최대 25초 걸린다)
    @Scheduled(fixedDelay = 1000)
    public void poll() {
        if (!enabled || !properties.isConfigured()) {
            return;
        }

        Instant now = clock.instant();
        if (now.isBefore(pausedUntil)) {
            return;
        }

        try {
            pollOnce(now);

        } catch (TelegramApiException e) {
            pauseAfter(e, now);

        } catch (RuntimeException e) {
            // 요청 주소에 토큰이 들어갈 수 있어 예외 메시지는 남기지 않고 종류만 남긴다
            log.warn("텔레그램 폴링 중 오류: {}", e.getClass().getSimpleName());
            pausedUntil = now.plus(ERROR_PAUSE);
        }

    }

    void pollOnce(Instant now) {
        if (offset == null) {
            syncOffset();
            return;
        }

        if (now.isAfter(lastPurge.plus(PURGE_INTERVAL))) {
            lastPurge = now;
            int purged = telegramLinkService.purgeExpired();
            if (purged > 0) {
                log.info("만료된 연동 코드 {}개를 정리했다", purged);
            }
        }

        List<TelegramUpdate> updates = telegramApi.getUpdates(offset, POLL_TIMEOUT_SECONDS);
        for (TelegramUpdate update : updates) {
            // 처리하다 실패해도 같은 업데이트를 다시 받지 않도록 먼저 전진시킨다
            offset = Math.max(offset, update.updateId() + 1);
            handle(update);
        }

    }

    // 시작할 때 한 번: 서버가 꺼져 있는 동안 쌓인 옛 메시지는 처리하지 않고 건너뛴다(이미 쓴 코드에 답장이 가는 것을 막는다).
    private void syncOffset() {
        List<TelegramUpdate> latest = telegramApi.getUpdates(-1, 0);
        offset = latest.isEmpty() ? 0L : latest.get(latest.size() - 1).updateId() + 1;
        log.info("텔레그램 폴링을 시작한다");

    }

    void handle(TelegramUpdate update) {
        if (!update.isPrivateText()) {
            return;
        }

        Matcher m = START.matcher(update.text().trim());
        if (!m.matches()) {
            return;     // /start 가 아닌 메시지에는 답하지 않는다
        }

        String payload = m.group(1) == null ? "" : m.group(1).trim();
        if (payload.isEmpty()) {
            reply(update.chatId(), REPLY_USAGE);
            return;
        }

        try {
            TelegramLinkService.LinkOutcome outcome = telegramLinkService.completeLink(payload, update.chatId());
            log.info("텔레그램 연동 확인 결과: {}", outcome);
            reply(update.chatId(), replyFor(outcome));

        } catch (RuntimeException e) {
            log.warn("텔레그램 연동 처리 중 오류: {}", e.getClass().getSimpleName());
        }

    }

    static String replyFor(TelegramLinkService.LinkOutcome outcome) {
        return switch (outcome) {
            case LINKED -> REPLY_LINKED;
            case INVALID_CODE -> REPLY_INVALID;
            case CHAT_ALREADY_LINKED -> REPLY_CHAT_IN_USE;
            case MEMBER_ALREADY_LINKED -> REPLY_MEMBER_LINKED;
        };

    }

    private void reply(long chatId, String text) {
        try {
            telegramApi.sendMessage(chatId, text);

        } catch (TelegramApiException e) {
            log.warn("연동 안내 답장을 보내지 못했다: {}", e.getMessage());
        }

    }

    private void pauseAfter(TelegramApiException e, Instant now) {
        Duration pause;
        if (e.status() == TelegramApiException.CONFLICT) {
            log.warn("getUpdates 충돌(HTTP 409): 같은 봇을 다른 곳에서 폴링하거나 웹훅이 켜져 있다");
            pause = CONFLICT_PAUSE;

        } else if (e.status() == TelegramApiException.RATE_LIMITED && e.retryAfterSeconds() > 0) {
            log.warn("텔레그램 요청 제한(HTTP 429): {}초 쉰다", e.retryAfterSeconds());
            pause = Duration.ofSeconds(e.retryAfterSeconds());

        } else {
            log.warn("{}", e.getMessage());
            pause = ERROR_PAUSE;
        }
        pausedUntil = now.plus(pause);

    }

}
