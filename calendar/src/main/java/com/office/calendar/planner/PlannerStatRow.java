package com.office.calendar.planner;

/** 통계 집계 한 줄: bucket(요일 1~7 또는 주차 1~6)별 일정 수. */
public record PlannerStatRow(int bucket, int cnt) {
}
