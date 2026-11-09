package com.office.calendar.brief.jpa;

// 주간 브리핑 발송 결과(brf-004)
public enum BriefResult {

    SUCCESS,    // 발송함
    FAIL,       // 발송하지 못함(봇 차단, 네트워크 오류 등)
    SKIP        // 보낼 일정이 없어 발송하지 않음

}
