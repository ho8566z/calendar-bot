package com.office.calendar.admin;

import com.office.calendar.admin.AdminUserApprovalService.RoleChangeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.Map;

// /admin/** 는 SecurityConfig에서 ADMIN만 들어올 수 있다
@Slf4j
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserApprovalController {

    final private AdminUserApprovalService approvalService;

    // 승인: PRE_USER → USER
    @PostMapping("/{no}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable("no") int no) {
        log.info("approve()");

        return toResponse(approvalService.approve(no));

    }

    // 승인 취소: USER → PRE_USER
    @PostMapping("/{no}/unapprove")
    public ResponseEntity<Map<String, Object>> unapprove(@PathVariable("no") int no) {
        log.info("unapprove()");

        return toResponse(approvalService.unapprove(no));

    }

    // 결과를 HTTP 상태와 본문(result, 오류 코드)으로 바꾼다
    private ResponseEntity<Map<String, Object>> toResponse(RoleChangeResult changeResult) {

        Map<String, Object> body = new HashMap<>();

        switch (changeResult.outcome()) {
            case CHANGED -> {
                body.put("result", 1);
                body.put("role", changeResult.role());
                body.put("mod_date", changeResult.modDate());
                return ResponseEntity.ok(body);                                 // 200 OK
            }
            case NOT_FOUND -> {
                body.put("result", 0);
                body.put("code", "MEMBER_NOT_FOUND");
                body.put("message", "회원을 찾을 수 없습니다.");
                return ResponseEntity.status(404).body(body);                   // 404 NOT FOUND
            }
            default -> {
                body.put("result", 0);
                body.put("code", "INVALID_STATUS");
                body.put("message", "현재 역할에서는 할 수 없는 작업입니다.");
                body.put("role", changeResult.role());
                return ResponseEntity.status(409).body(body);                   // 409 CONFLICT
            }
        }

    }

}
