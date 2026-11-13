package com.office.calendar.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

// 텔레그램 연동·해제와 브리핑 수신 설정(USER 전용). 대상 회원은 항상 로그인한 본인(Principal)이다.
@Slf4j
@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
public class TelegramLinkController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // PUT /member/brief 요청 본문
    public record BriefRequest(Boolean enabled) {
    }

    final private TelegramLinkService telegramLinkService;

    // 연동 코드 발급
    @PostMapping("/telegram/link")
    public ResponseEntity<Map<String, Object>> issueCode(Principal principal) {
        log.info("issueCode()");

        TelegramLinkService.IssuedLink issued = telegramLinkService.issueCode(principal.getName());

        switch (issued.outcome()) {
            case NOT_CONFIGURED:
                return error(HttpStatus.SERVICE_UNAVAILABLE, "TELEGRAM_NOT_CONFIGURED", "텔레그램 봇이 설정되지 않았습니다.");
            case ALREADY_LINKED:
                return error(HttpStatus.CONFLICT, "ALREADY_LINKED", "이미 텔레그램이 연동돼 있습니다.");
            default:
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("code", issued.code());
                body.put("link", issued.link());
                body.put("expires_at", issued.expiresAt().format(TIME_FORMAT));
                return ResponseEntity.ok(body);
        }

    }

    // 연동 상태 조회(연동 대기 화면이 주기적으로 부른다)
    @GetMapping("/telegram")
    public ResponseEntity<Map<String, Object>> getStatus(Principal principal) {
        log.info("getStatus()");

        return ResponseEntity.ok(statusBody(telegramLinkService.getStatus(principal.getName())));

    }

    // 연동 해제
    @DeleteMapping("/telegram")
    public ResponseEntity<Map<String, Object>> unlink(Principal principal) {
        log.info("unlink()");

        return ResponseEntity.ok(statusBody(telegramLinkService.unlink(principal.getName())));

    }

    // 브리핑 수신 켜기/끄기
    @PutMapping("/brief")
    public ResponseEntity<Map<String, Object>> setBrief(@RequestBody(required = false) BriefRequest request, Principal principal) {
        log.info("setBrief()");

        if (request == null || request.enabled() == null) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_ENABLED", "enabled는 true 또는 false여야 합니다.");
        }

        TelegramLinkService.BriefOutcome outcome = telegramLinkService.setBriefEnabled(principal.getName(), request.enabled());
        if (outcome == TelegramLinkService.BriefOutcome.NOT_LINKED) {
            return error(HttpStatus.CONFLICT, "TELEGRAM_NOT_LINKED", "텔레그램을 먼저 연동해야 수신을 켤 수 있습니다.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", request.enabled());
        return ResponseEntity.ok(body);

    }

    private Map<String, Object> statusBody(TelegramLinkService.Status status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("linked", status.linked());
        body.put("brief_enabled", status.briefEnabled());
        return body;

    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);

    }

}
