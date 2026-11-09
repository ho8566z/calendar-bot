package com.office.calendar.planner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 통계 집계 한 줄: bucket(요일 1~7 또는 주차 1~6)별 일정 수
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlannerStatRow {

    private int bucket;
    private int cnt;

}
