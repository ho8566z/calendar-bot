package com.office.calendar.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

// /admin/** 는 SecurityConfig에서 ADMIN만 들어올 수 있다
@Slf4j
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    final private AdminUserService adminUserService;

    // 사용자 관리 화면(/admin/users). 목록은 화면의 JS가 /admin/users/data로 가져온다
    @GetMapping({"", "/"})
    public String list() {
        log.info("list()");

        String nextPage = "admin/users";

        return nextPage;

    }

    // 사용자 목록·검색 데이터(JSON)
    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> data(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        log.info("data()");

        Map<String, Object> resultMap = adminUserService.getUsers(keyword, page, size);

        return ResponseEntity.ok(resultMap);

    }

}
