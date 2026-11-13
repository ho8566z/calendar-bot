package com.office.calendar.brief;

/**
 * 브리핑에 넣을 일정 한 건(제목·날짜·시작 시간만).
 *
 * @param startTime HH:mm, 시간이 없으면 null
 */
public record BriefPlanRow(int year, int month, int date, String title, String startTime) {
}
