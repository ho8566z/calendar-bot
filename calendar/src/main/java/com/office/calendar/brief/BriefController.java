package com.office.calendar.brief;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

// 브리핑 "지금 보내기"(USER 전용). 보내는 대상은 항상 로그인한 본인(Principal)이다.
@Slf4j
@RestController
@RequestMapping("/member/brief")
@RequiredArgsConstructor
public class BriefController {

    final private BriefNowService briefNowService;

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendNow(Principal principal) {
        log.info("sendNow()");

        BriefNowService.Result result = briefNowService.sendNow(principal.getName());

        switch (result.outcome()) {
            case NOT_CONFIGURED:
                return error(HttpStatus.SERVICE_UNAVAILABLE, "TELEGRAM_NOT_CONFIGURED", "텔레그램 봇이 설정되지 않았습니다.", null);
            case NOT_LINKED:
                return error(HttpStatus.CONFLICT, "TELEGRAM_NOT_LINKED", "텔레그램을 먼저 연동해야 합니다.", null);
            case TOO_FREQUENT:
                return error(HttpStatus.TOO_MANY_REQUESTS, "TOO_FREQUENT", "잠시 후 다시 시도해 주세요.", result.retryAfterSeconds());
            case SEND_FAILED:
                return error(HttpStatus.BAD_GATEWAY, result.blocked() ? "BOT_BLOCKED" : "SEND_FAILED",
                        result.blocked() ? "텔레그램에서 봇을 차단했는지 확인해 주세요." : "텔레그램으로 보내지 못했습니다.", null);
            default:
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("sent", true);
                body.put("week_start", result.weekStart().toString());
                body.put("empty", result.empty());
                return ResponseEntity.ok(body);
        }

    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message, Integer retryAfter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        if (retryAfter != null) {
            body.put("retry_after", retryAfter);
        }
        return ResponseEntity.status(status).body(body);

    }

}
