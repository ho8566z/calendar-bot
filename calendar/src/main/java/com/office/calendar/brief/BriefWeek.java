package com.office.calendar.brief;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

// 브리핑 대상 주 계산. 한 주는 월요일에 시작해 일요일에 끝난다.
// 매주 일요일 저녁에 보내므로 "오늘 이후 처음 오는 월요일"부터 7일이 다음 주 브리핑 범위다.
public final class BriefWeek {

    private BriefWeek() {
    }

    // today 다음에 처음 오는 월요일(today가 월요일이어도 다음 주 월요일). 일요일이면 바로 다음 날이다.
    public static LocalDate nextWeekStart(LocalDate today) {
        return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    // 그 주의 일요일
    public static LocalDate weekEnd(LocalDate weekStart) {
        return weekStart.plusDays(6);
    }

}
