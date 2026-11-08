package com.office.calendar.telegram;

// 텔레그램 Bot API 호출 실패. 봇 토큰이 요청 주소 경로에 들어가므로(brf-007) 주소·응답 본문·원인 예외를 담지 않는다.
// 메시지는 HTTP 상태 번호 같은 안전한 정보만 쓴다.
public class TelegramApiException extends RuntimeException {

    public static final int NETWORK_ERROR = 0;       // 연결 실패·시간 초과 등(응답 없음)
    public static final int BLOCKED = 403;           // 사용자가 봇을 차단함
    public static final int RATE_LIMITED = 429;      // 요청 제한
    public static final int CONFLICT = 409;          // 웹훅이 켜져 있거나 다른 getUpdates 요청이 동시에 있음

    private final int status;
    private final int retryAfterSeconds;

    public TelegramApiException(int status, int retryAfterSeconds) {
        super(status == NETWORK_ERROR ? "텔레그램 요청 실패(연결 오류)" : "텔레그램 요청 실패(HTTP " + status + ")");
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    // HTTP 상태 번호. 응답이 없으면 NETWORK_ERROR(0)
    public int status() {
        return status;
    }

    // 429일 때 텔레그램이 알려 준 대기 초(없으면 0)
    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public boolean isBlocked() {
        return status == BLOCKED;
    }

}
