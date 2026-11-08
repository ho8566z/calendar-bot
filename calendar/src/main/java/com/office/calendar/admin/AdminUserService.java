package com.office.calendar.admin;

import com.office.calendar.admin.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 관리자 사용자 목록·검색(adm-001). 조회 전용이라 읽기 전용 트랜잭션으로 분리한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    public static final int DEFAULT_SIZE = 20;      // size가 없거나 잘못됐을 때
    public static final int MAX_SIZE = 100;         // 한 번에 가져올 수 있는 최대 건수

    final private AdminUserMapper adminUserMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> getUsers(String keyword, int page, int size) {
        log.info("getUsers()");

        String likeKeyword = toLikeKeyword(keyword);
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int safePage = Math.max(page, 0);
        int offset = (int) Math.min((long) safePage * safeSize, Integer.MAX_VALUE);

        List<AdminUserDto> users = adminUserMapper.selectUsers(likeKeyword, safeSize, offset);
        int total = adminUserMapper.countUsers(likeKeyword);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("users", users);
        resultMap.put("total", total);
        resultMap.put("page", safePage);
        resultMap.put("size", safeSize);

        return resultMap;

    }

    // 검색어를 LIKE에 넣기 좋게 바꾼다: 비어 있으면 null(조건 없음), %·_ 는 문자로 취급하도록 '!'로 이스케이프
    static String toLikeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

    }

}
