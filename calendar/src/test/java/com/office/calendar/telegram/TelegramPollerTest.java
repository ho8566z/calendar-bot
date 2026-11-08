package com.office.calendar.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 텔레그램 폴러: 시작 동기화, 연동 처리와 답장, 오류 뒤 쉬기
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramPollerTest {

    private static final ZoneId ZONE = ZoneOffset.UTC;

    @Mock
    private TelegramApi api;
    @Mock
    private TelegramLinkService linkService;

    private MutableClock clock;
    private TelegramPoller poller;

    // 시각을 직접 움직일 수 있는 시계
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    private TelegramPoller poller(boolean enabled, TelegramProperties props) {
        return new TelegramPoller(api, linkService, props, clock, enabled);
    }

    private static TelegramUpdate msg(long updateId, long chatId, String text) {
        return new TelegramUpdate(updateId, chatId, "private", text);
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        poller = poller(true, new TelegramProperties("tok", "MyCalBot", 10));
    }

    // 시작 동기화를 끝낸 상태로 만든다(최근 업데이트 없음 → offset 0)
    private void synced() {
        when(api.getUpdates(-1, 0)).thenReturn(List.of());
        poller.poll();
        org.mockito.Mockito.clearInvocations(api);
    }

    @Test
    @DisplayName("설정이 없거나 꺼져 있으면 아무 요청도 하지 않는다")
    void disabledOrNotConfigured() {
        poller(true, new TelegramProperties("", "", 10)).poll();
        poller(false, new TelegramProperties("tok", "MyCalBot", 10)).poll();

        verify(api, never()).getUpdates(anyLong(), anyInt());
    }

    @Test
    @DisplayName("시작 동기화: 쌓여 있던 옛 업데이트는 처리하지 않고 건너뛴다")
    void startupSkipsOldUpdates() {
        when(api.getUpdates(-1, 0)).thenReturn(List.of(msg(41, 555, "/start OLDCODE")));

        poller.poll();

        verify(linkService, never()).completeLink(anyString(), anyLong());
        verify(api, never()).sendMessage(anyLong(), anyString());

        // 다음 폴링은 가장 최근 id 다음부터 요청한다
        poller.poll();
        verify(api).getUpdates(42, TelegramPoller.POLL_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("시작 동기화: 쌓인 것이 없으면 offset 0부터")
    void startupWithNothing() {
        synced();

        poller.poll();

        verify(api).getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("'/start 코드'를 받으면 연동을 확인하고 결과를 답장한다")
    void startWithCode() {
        synced();
        when(api.getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS)).thenReturn(List.of(msg(7, 555, "/start CODE1234")));
        when(linkService.completeLink("CODE1234", 555L)).thenReturn(TelegramLinkService.LinkOutcome.LINKED);

        poller.poll();

        verify(linkService).completeLink("CODE1234", 555L);
        verify(api).sendMessage(555L, TelegramPoller.REPLY_LINKED);
    }

    @Test
    @DisplayName("연동 결과마다 알맞은 안내 문구를 보낸다")
    void repliesPerOutcome() {
        assertThat(TelegramPoller.replyFor(TelegramLinkService.LinkOutcome.LINKED)).isEqualTo(TelegramPoller.REPLY_LINKED);
        assertThat(TelegramPoller.replyFor(TelegramLinkService.LinkOutcome.INVALID_CODE)).isEqualTo(TelegramPoller.REPLY_INVALID);
        assertThat(TelegramPoller.replyFor(TelegramLinkService.LinkOutcome.CHAT_ALREADY_LINKED)).isEqualTo(TelegramPoller.REPLY_CHAT_IN_USE);
        assertThat(TelegramPoller.replyFor(TelegramLinkService.LinkOutcome.MEMBER_ALREADY_LINKED)).isEqualTo(TelegramPoller.REPLY_MEMBER_LINKED);
    }

    @Test
    @DisplayName("코드 없는 '/start'는 사용법을 안내하고 연동을 시도하지 않는다")
    void startWithoutCode() {
        synced();
        when(api.getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS)).thenReturn(List.of(msg(7, 555, "/start"), msg(8, 556, "/start   ")));

        poller.poll();

        verify(api).sendMessage(555L, TelegramPoller.REPLY_USAGE);
        verify(api).sendMessage(556L, TelegramPoller.REPLY_USAGE);
        verify(linkService, never()).completeLink(anyString(), anyLong());
    }

    @Test
    @DisplayName("'/start@봇이름 코드' 형식도 받는다")
    void startWithBotName() {
        synced();
        when(api.getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS)).thenReturn(List.of(msg(7, 555, "/start@MyCalBot CODE1234")));
        when(linkService.completeLink("CODE1234", 555L)).thenReturn(TelegramLinkService.LinkOutcome.LINKED);

        poller.poll();

        verify(linkService).completeLink("CODE1234", 555L);
    }

    @Test
    @DisplayName("/start가 아닌 메시지, 그룹 대화, 텍스트 없는 업데이트는 무시하고 답하지 않는다")
    void ignoresOthers() {
        synced();
        when(api.getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS)).thenReturn(List.of(
                msg(1, 555, "hello"),
                msg(2, 555, "/startx CODE"),
                msg(3, 555, "x /start CODE"),
                new TelegramUpdate(4, -100L, "group", "/start CODE1234"),
                new TelegramUpdate(5, null, null, null)));

        poller.poll();

        verify(linkService, never()).completeLink(anyString(), anyLong());
        verify(api, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    @DisplayName("업데이트를 처리한 만큼 offset이 전진한다(최대 id + 1)")
    void offsetAdvances() {
        synced();
        when(api.getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS)).thenReturn(List.of(msg(10, 1, "hi"), msg(12, 1, "hi"), msg(11, 1, "hi")));

        poller.poll();
        poller.poll();

        verify(api).getUpdates(13, TelegramPoller.POLL_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("처리 중 오류가 나도 같은 업데이트를 다시 받지 않고 다음 업데이트를 계속 처리한다")
    void poisonUpdateDoesNotLoop() {
        synced();
        when(api.getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS)).thenReturn(List.of(msg(7, 555, "/start BAD"), msg(8, 556, "/start GOOD")));
        when(linkService.completeLink("BAD", 555L)).thenThrow(new IllegalStateException("db down"));
        when(linkService.completeLink("GOOD", 556L)).thenReturn(TelegramLinkService.LinkOutcome.LINKED);

        poller.poll();

        verify(api).sendMessage(556L, TelegramPoller.REPLY_LINKED);
        poller.poll();
        verify(api).getUpdates(9, TelegramPoller.POLL_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("답장을 보내지 못해도(차단 등) 폴링은 계속된다")
    void replyFailureIgnored() {
        synced();
        when(api.getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS)).thenReturn(List.of(msg(7, 555, "/start CODE1234")));
        when(linkService.completeLink("CODE1234", 555L)).thenReturn(TelegramLinkService.LinkOutcome.LINKED);
        doThrow(new TelegramApiException(403, 0)).when(api).sendMessage(anyLong(), anyString());

        poller.poll();
        poller.poll();

        verify(api).getUpdates(8, TelegramPoller.POLL_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("API 오류가 나면 10초 쉬었다가 다시 시도한다")
    void pausesAfterError() {
        synced();
        when(api.getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS)).thenThrow(new TelegramApiException(500, 0));

        poller.poll();
        poller.poll();      // 쉬는 중
        verify(api, times(1)).getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS);

        clock.advance(Duration.ofSeconds(9));
        poller.poll();
        verify(api, times(1)).getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS);

        clock.advance(Duration.ofSeconds(2));
        poller.poll();
        verify(api, times(2)).getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("409(충돌)는 30초, 429는 알려 준 초만큼 쉰다")
    void pauseLengths() {
        synced();
        when(api.getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS)).thenThrow(new TelegramApiException(409, 0));
        poller.poll();
        clock.advance(Duration.ofSeconds(29));
        poller.poll();
        verify(api, times(1)).getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS);
        clock.advance(Duration.ofSeconds(2));
        doThrow(new TelegramApiException(429, 60)).when(api).getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS);
        poller.poll();
        verify(api, times(2)).getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS);

        clock.advance(Duration.ofSeconds(59));
        poller.poll();
        verify(api, times(2)).getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS);
        clock.advance(Duration.ofSeconds(2));
        poller.poll();
        verify(api, times(3)).getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("예상 못 한 오류도 폴링 루프를 죽이지 않는다")
    void unexpectedErrorSurvives() {
        synced();
        when(api.getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS)).thenThrow(new IllegalStateException("boom"));

        poller.poll();

        clock.advance(Duration.ofSeconds(11));
        doReturn(List.of()).when(api).getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS);
        poller.poll();
        verify(api, times(2)).getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("시작 동기화가 실패하면 쉬었다가 처음부터 다시 동기화한다")
    void syncFailureRetries() {
        when(api.getUpdates(-1, 0)).thenThrow(new TelegramApiException(0, 0));

        poller.poll();
        clock.advance(Duration.ofSeconds(11));
        doReturn(List.of()).when(api).getUpdates(-1, 0);
        poller.poll();

        verify(api, times(2)).getUpdates(-1, 0);
        verify(linkService, never()).completeLink(anyString(), anyLong());
    }

    @Test
    @DisplayName("만료된 연동 코드는 5분마다 정리한다")
    void purgesPeriodically() {
        synced();
        when(api.getUpdates(0, TelegramPoller.POLL_TIMEOUT_SECONDS)).thenReturn(List.of());

        poller.poll();      // 처음에는 정리
        poller.poll();      // 5분이 안 지났으므로 정리 안 함
        verify(linkService, times(1)).purgeExpired();

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        poller.poll();
        verify(linkService, times(2)).purgeExpired();
    }

}
