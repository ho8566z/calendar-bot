package com.office.calendar.brief;

import com.office.calendar.telegram.TelegramApi;
import com.office.calendar.telegram.TelegramApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.LongConsumer;

// 텔레그램 sendMessage로 브리핑을 보낸다. 요청 제한(429)은 짧게 기다렸다가 한 번만 다시 시도한다.
// 메시지 본문(일정 제목)과 chat ID는 로그에 남기지 않는다.
@Slf4j
@Component
public class TelegramBriefSender implements BriefSender {

    // 429에서 기다릴 수 있는 최대 초. 이보다 오래 기다려야 하면 이번에는 실패로 둔다
    static final int MAX_RETRY_WAIT_SECONDS = 5;

    private final TelegramApi telegramApi;
    private final LongConsumer sleeper;

    @Autowired
    public TelegramBriefSender(TelegramApi telegramApi) {
        this(telegramApi, TelegramBriefSender::sleepMillis);
    }

    TelegramBriefSender(TelegramApi telegramApi, LongConsumer sleeper) {
        this.telegramApi = telegramApi;
        this.sleeper = sleeper;
    }

    @Override
    public void send(long chatId, List<String> parts) {
        for (String part : parts) {
            sendOne(chatId, part);
        }

    }

    private void sendOne(long chatId, String text) {
        try {
            telegramApi.sendMessage(chatId, text);

        } catch (TelegramApiException e) {
            if (e.status() == TelegramApiException.RATE_LIMITED
                    && e.retryAfterSeconds() > 0 && e.retryAfterSeconds() <= MAX_RETRY_WAIT_SECONDS) {
                log.info("텔레그램 요청 제한: {}초 뒤 한 번 다시 시도한다", e.retryAfterSeconds());
                sleeper.accept(e.retryAfterSeconds() * 1000L);
                telegramApi.sendMessage(chatId, text);
                return;
            }
            throw e;
        }

    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

}
