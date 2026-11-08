package com.office.calendar.brief;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 브리핑에 넣을 일정 한 건(제목·날짜·시작 시간만)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BriefPlanRow {

    private int year;
    private int month;
    private int date;
    private String title;
    private String startTime;       // HH:mm, 시간이 없으면 null

}
