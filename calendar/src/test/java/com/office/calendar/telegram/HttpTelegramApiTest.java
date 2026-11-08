package com.office.calendar.telegram;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 텔레그램 Bot API 호출: 가짜 서버로 실제 HTTP 요청·응답을 확인한다
class HttpTelegramApiTest {

    private static final String TOKEN = "123456:SECRET-token_VALUE";

    private FakeTelegramServer server;
    private HttpTelegramApi api;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeTelegramServer();
        api = new HttpTelegramApi(new TelegramProperties(TOKEN, "MyCalBot", 10), server.baseUrl());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("getUpdates: 토큰이 든 경로로 offset·timeout을 보내고 업데이트를 읽는다")
    void getUpdates_request() {
        server.respond(200, """
                {"ok":true,"result":[
                  {"update_id":100,"message":{"chat":{"id":555,"type":"private"},"text":"/start CODE1234"}},
                  {"update_id":101,"message":{"chat":{"id":-1001234567890,"type":"supergroup"},"text":"hello"}},
                  {"update_id":102,"edited_message":{"text":"x"}}
                ]}""");

        List<TelegramUpdate> updates = api.getUpdates(100, 25);

        FakeTelegramServer.Request req = server.last();
        assertThat(req.method()).isEqualTo("GET");
        assertThat(req.pathAndQuery()).startsWith("/bot" + TOKEN + "/getUpdates?");
        assertThat(req.pathAndQuery()).contains("offset=100").contains("timeout=25");

        assertThat(updates).hasSize(3);
        assertThat(updates.get(0)).isEqualTo(new TelegramUpdate(100, 555L, "private", "/start CODE1234"));
        assertThat(updates.get(0).isPrivateText()).isTrue();
        assertThat(updates.get(1).chatId()).isEqualTo(-1001234567890L);     // int 범위를 넘는 chat ID
        assertThat(updates.get(1).isPrivateText()).isFalse();
        assertThat(updates.get(2).chatId()).isNull();                       // 메시지가 아닌 업데이트
        assertThat(updates.get(2).isPrivateText()).isFalse();
    }

    @Test
    @DisplayName("getUpdates: 새 업데이트가 없으면 빈 목록")
    void getUpdates_empty() {
        assertThat(api.getUpdates(0, 0)).isEmpty();
    }

    @Test
    @DisplayName("getUpdates: offset -1도 그대로 보낸다(가장 최근 1건만)")
    void getUpdates_negativeOffset() {
        api.getUpdates(-1, 0);

        assertThat(server.last().pathAndQuery()).contains("offset=-1");
    }

    @Test
    @DisplayName("sendMessage: JSON으로 chat_id와 text만 보낸다(parse_mode 없음)")
    void sendMessage_request() {
        api.sendMessage(555L, "안녕하세요 *굵게* _기울임_");

        FakeTelegramServer.Request req = server.last();
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.pathAndQuery()).isEqualTo("/bot" + TOKEN + "/sendMessage");
        assertThat(req.body()).contains("\"chat_id\":555").contains("안녕하세요 *굵게* _기울임_");
        assertThat(req.body()).doesNotContain("parse_mode");
    }

    @Test
    @DisplayName("sendMessage: 403(봇 차단)은 status 403, isBlocked")
    void sendMessage_blocked() {
        server.respond(403, "{\"ok\":false,\"error_code\":403,\"description\":\"Forbidden: bot was blocked by the user\"}");

        assertThatThrownBy(() -> api.sendMessage(555L, "hi"))
                .isInstanceOfSatisfying(TelegramApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(403);
                    assertThat(e.isBlocked()).isTrue();
                });
    }

    @Test
    @DisplayName("sendMessage: 429는 retry_after를 읽는다")
    void sendMessage_rateLimited() {
        server.respond(429, "{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests: retry after 7\",\"parameters\":{\"retry_after\":7}}");

        assertThatThrownBy(() -> api.sendMessage(555L, "hi"))
                .isInstanceOfSatisfying(TelegramApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(429);
                    assertThat(e.retryAfterSeconds()).isEqualTo(7);
                });
    }

    @Test
    @DisplayName("429에 retry_after가 없으면 0")
    void rateLimited_withoutRetryAfter() {
        server.respond(429, "{\"ok\":false}");

        assertThatThrownBy(() -> api.sendMessage(555L, "hi"))
                .isInstanceOfSatisfying(TelegramApiException.class, e -> assertThat(e.retryAfterSeconds()).isZero());
    }

    @Test
    @DisplayName("getUpdates: 409(충돌)와 500 같은 오류는 상태 번호를 담아 던진다")
    void getUpdates_errors() {
        for (int status : new int[]{409, 500, 400, 401}) {
            server.respond(status, "{\"ok\":false}");

            assertThatThrownBy(() -> api.getUpdates(0, 0))
                    .isInstanceOfSatisfying(TelegramApiException.class, e -> assertThat(e.status()).isEqualTo(status));
        }
    }

    @Test
    @DisplayName("HTTP 200이어도 ok가 true가 아니면 실패로 본다")
    void notOk() {
        server.respond(200, "{\"ok\":false}");

        assertThatThrownBy(() -> api.sendMessage(555L, "hi")).isInstanceOf(TelegramApiException.class);
    }

    @Test
    @DisplayName("연결할 수 없으면 NETWORK_ERROR")
    void connectionRefused() throws Exception {
        int freePort;
        try (ServerSocket s = new ServerSocket(0)) {
            freePort = s.getLocalPort();
        }
        HttpTelegramApi unreachable = new HttpTelegramApi(new TelegramProperties(TOKEN, "MyCalBot", 10), "http://127.0.0.1:" + freePort);

        assertThatThrownBy(() -> unreachable.sendMessage(1L, "hi"))
                .isInstanceOfSatisfying(TelegramApiException.class, e -> assertThat(e.status()).isEqualTo(TelegramApiException.NETWORK_ERROR));
    }

    @Test
    @DisplayName("보안: 어떤 실패에서도 예외 메시지·원인에 토큰이나 요청 주소가 없다")
    void exceptionsNeverLeakToken() throws Exception {
        server.respond(403, "{\"ok\":false,\"description\":\"" + TOKEN + "\"}");
        int freePort;
        try (ServerSocket s = new ServerSocket(0)) {
            freePort = s.getLocalPort();
        }
        HttpTelegramApi unreachable = new HttpTelegramApi(new TelegramProperties(TOKEN, "MyCalBot", 10), "http://127.0.0.1:" + freePort);

        for (Runnable call : List.<Runnable>of(() -> api.sendMessage(1L, "x"), () -> api.getUpdates(0, 0), () -> unreachable.sendMessage(1L, "x"))) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(TelegramApiException.class, e -> {
                assertThat(e.getMessage()).doesNotContain("SECRET").doesNotContain("/bot").doesNotContain("127.0.0.1");
                assertThat(e.getCause()).isNull();
                assertThat(e.toString()).doesNotContain("SECRET");
            });
        }
    }

    @Test
    @DisplayName("기본 주소 설정 끝의 슬래시는 중복되지 않는다")
    void trailingSlash() {
        HttpTelegramApi slash = new HttpTelegramApi(new TelegramProperties(TOKEN, "MyCalBot", 10), server.baseUrl() + "/");

        slash.sendMessage(1L, "x");

        assertThat(server.last().pathAndQuery()).isEqualTo("/bot" + TOKEN + "/sendMessage");
    }

}
