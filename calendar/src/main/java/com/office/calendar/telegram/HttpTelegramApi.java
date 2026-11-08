package com.office.calendar.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Spring RestClient로 텔레그램 Bot API를 호출한다(추가 의존성 없음).
// 봇 토큰이 요청 주소 경로에 들어가므로 주소·응답 본문·원인 예외를 로그나 예외 메시지에 남기지 않는다(brf-007).
@Slf4j
@Component
public class HttpTelegramApi implements TelegramApi {

    private static final Pattern RETRY_AFTER = Pattern.compile("\"retry_after\"\\s*:\\s*(\\d+)");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(15);
    private static final int POLL_MARGIN_SECONDS = 10;      // 롱 폴링 대기 시간보다 읽기 제한을 이만큼 더 둔다

    private final TelegramProperties properties;
    private final String apiBase;
    private final RestClient sendClient;

    public HttpTelegramApi(TelegramProperties properties,
                           @Value("${telegram.api-base:https://api.telegram.org}") String apiBase) {
        this.properties = properties;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.sendClient = newClient(SEND_TIMEOUT);
    }

    private static RestClient newClient(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
        RestClient pollClient = newClient(Duration.ofSeconds(timeoutSeconds + POLL_MARGIN_SECONDS));

        Map<String, Object> body = call(() -> pollClient.get()
                .uri(botUrl("getUpdates") + "?offset={offset}&timeout={timeout}&allowed_updates={allowed}",
                        offset, timeoutSeconds, "[\"message\"]")
                .retrieve()
                .body(Map.class));

        List<TelegramUpdate> updates = new ArrayList<>();
        Object result = body.get("result");
        if (result instanceof List) {
            for (Object item : (List<Object>) result) {
                if (item instanceof Map && ((Map<String, Object>) item).get("update_id") instanceof Number) {
                    updates.add(TelegramUpdate.fromMap((Map<String, Object>) item));
                }
            }
        }
        return updates;

    }

    @Override
    public void sendMessage(long chatId, String text) {
        call(() -> sendClient.post()
                .uri(botUrl("sendMessage"))
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .body(Map.class));

    }

    // 토큰이 든 호출 주소. 토큰의 콜론이 %3A 로 바뀌지 않도록 변수가 아니라 주소 글자 그대로 넣는다
    // (TelegramProperties가 토큰에 주소에 안전한 문자만 허용하므로 주소·템플릿이 깨지지 않는다).
    private String botUrl(String method) {
        if (!properties.hasValidToken()) {
            throw new TelegramApiException(TelegramApiException.NETWORK_ERROR, 0);
        }
        return apiBase + "/bot" + properties.botToken() + "/" + method;
    }

    // 호출하고 응답이 ok인지 확인한다. 어떤 실패든 안전한 메시지의 TelegramApiException으로 바꿔 던진다
    private Map<String, Object> call(java.util.function.Supplier<Map<String, Object>> request) {
        Map<String, Object> body;
        try {
            body = request.get();

        } catch (RestClientResponseException e) {
            throw new TelegramApiException(e.getStatusCode().value(), retryAfter(e.getResponseBodyAsString()));

        } catch (RestClientException e) {
            throw new TelegramApiException(TelegramApiException.NETWORK_ERROR, 0);

        }

        if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
            // HTTP 200인데 ok가 아닌 경우: 상태를 알 수 없으므로 일반 실패로 본다(본문은 남기지 않는다)
            throw new TelegramApiException(TelegramApiException.NETWORK_ERROR, 0);
        }
        return body;

    }

    private static int retryAfter(String responseBody) {
        if (responseBody == null) {
            return 0;
        }
        Matcher m = RETRY_AFTER.matcher(responseBody);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;

    }

}
