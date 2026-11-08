package com.office.calendar.brief;

import com.office.calendar.telegram.TelegramApi;
import com.office.calendar.telegram.TelegramApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 텔레그램 발송기: 조각 순서, 429 재시도, 실패 시 중단
class TelegramBriefSenderTest {

    private TelegramApi api;
    private List<Long> sleeps;
    private TelegramBriefSender sender;

    @BeforeEach
    void setUp() {
        api = Mockito.mock(TelegramApi.class);
        sleeps = new ArrayList<>();
        sender = new TelegramBriefSender(api, sleeps::add);
    }

    @Test
    @DisplayName("조각을 순서대로 보낸다")
    void sendsInOrder() {
        sender.send(555L, List.of("1", "2", "3"));

        InOrder order = inOrder(api);
        order.verify(api).sendMessage(555L, "1");
        order.verify(api).sendMessage(555L, "2");
        order.verify(api).sendMessage(555L, "3");
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("조각이 없으면 아무것도 보내지 않는다")
    void nothingToSend() {
        sender.send(555L, List.of());

        verify(api, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    @DisplayName("429(3초 대기)는 기다렸다가 한 번 다시 시도하고 성공하면 이어서 보낸다")
    void retriesOnceOnRateLimit() {
        doThrow(new TelegramApiException(429, 3)).doNothing().when(api).sendMessage(555L, "1");

        sender.send(555L, List.of("1", "2"));

        verify(api, times(2)).sendMessage(555L, "1");
        verify(api).sendMessage(555L, "2");
        assertThat(sleeps).containsExactly(3000L);
    }

    @Test
    @DisplayName("재시도도 실패하면 예외를 던지고 남은 조각은 보내지 않는다(재시도는 한 번뿐)")
    void retryFailsAgain() {
        doThrow(new TelegramApiException(429, 2)).when(api).sendMessage(555L, "1");

        assertThatThrownBy(() -> sender.send(555L, List.of("1", "2")))
                .isInstanceOfSatisfying(TelegramApiException.class, e -> assertThat(e.status()).isEqualTo(429));

        verify(api, times(2)).sendMessage(555L, "1");
        verify(api, never()).sendMessage(555L, "2");
    }

    @Test
    @DisplayName("429라도 5초보다 오래 기다려야 하면 기다리지 않고 바로 실패")
    void tooLongToWait() {
        doThrow(new TelegramApiException(429, 6)).when(api).sendMessage(555L, "1");

        assertThatThrownBy(() -> sender.send(555L, List.of("1"))).isInstanceOf(TelegramApiException.class);

        verify(api, times(1)).sendMessage(555L, "1");
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("429에 대기 시간 정보가 없으면(0) 재시도하지 않는다")
    void noRetryAfter() {
        doThrow(new TelegramApiException(429, 0)).when(api).sendMessage(555L, "1");

        assertThatThrownBy(() -> sender.send(555L, List.of("1"))).isInstanceOf(TelegramApiException.class);

        verify(api, times(1)).sendMessage(555L, "1");
    }

    @Test
    @DisplayName("봇 차단(403)이나 네트워크 오류는 재시도하지 않고 바로 던진다")
    void noRetryForOtherErrors() {
        for (int status : new int[]{403, 400, 500, TelegramApiException.NETWORK_ERROR}) {
            TelegramApi a = Mockito.mock(TelegramApi.class);
            doThrow(new TelegramApiException(status, 0)).when(a).sendMessage(anyLong(), anyString());

            assertThatThrownBy(() -> new TelegramBriefSender(a, sleeps::add).send(1L, List.of("x")))
                    .isInstanceOfSatisfying(TelegramApiException.class, e -> assertThat(e.status()).isEqualTo(status));
            verify(a, times(1)).sendMessage(1L, "x");
        }
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("중간 조각이 실패하면 앞 조각은 이미 보낸 상태로 두고 예외를 던진다")
    void failureInMiddle() {
        doNothing().when(api).sendMessage(555L, "1");
        doThrow(new TelegramApiException(403, 0)).when(api).sendMessage(555L, "2");

        assertThatThrownBy(() -> sender.send(555L, List.of("1", "2", "3"))).isInstanceOf(TelegramApiException.class);

        verify(api).sendMessage(555L, "1");
        verify(api, never()).sendMessage(555L, "3");
    }

}
