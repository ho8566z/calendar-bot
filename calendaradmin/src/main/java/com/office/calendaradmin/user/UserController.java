package com.office.calendaradmin.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    final private UserService userService;

    // 사용자 목록 화면(/user/list)
    @GetMapping("/list")
    public String list() {
        log.info("list()");

        String nextPage = "user/list";

        return nextPage;

    }

    // 사용자 목록 조회(/user/users)
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> users() {
        log.info("users()");

        Map<String, Object> resultMap = userService.users();

        return ResponseEntity.ok(resultMap);

    }

    // 사용자 권한 변경(/user/${userNo}/auth)
    @PutMapping("{userNo}/auth")
    public ResponseEntity<Map<String, Object>> updateUserAuthority(
            @PathVariable int userNo,
            @RequestBody Map<String, Integer> request
    ) {
        log.info("updateUserAuthority()");

        if (request.get("authorityNo") == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("result", 0));
        }

        Map<String, Object> resultMap =
                userService.updateUserAuthority(userNo, request.get("authorityNo"));


        return ResponseEntity.ok(resultMap);

    }



}
