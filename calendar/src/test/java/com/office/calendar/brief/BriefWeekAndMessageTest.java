package com.office.calendar.brief;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 브리핑 주 계산과 메시지 만들기
class BriefWeekAndMessageTest {

    private static BriefPlanRow plan(int y, int m, int d, String title, String time) {
        return new BriefPlanRow(y, m, d, title, time);
    }

    // ---------------------------------------------------------------- 주 계산

    @Test
    @DisplayName("대상 주: 일요일에 실행하면 바로 다음 날(월요일)부터")
    void nextWeekStart_fromSunday() {
        assertThat(BriefWeek.nextWeekStart(LocalDate.of(2026, 11, 8))).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    @DisplayName("대상 주: 월~토에 실행하면 그다음 월요일, 월요일에 실행해도 다음 주 월요일")
    void nextWeekStart_otherDays() {
        LocalDate nextMonday = LocalDate.of(2026, 11, 16);
        for (int d = 9; d <= 14; d++) {
            assertThat(BriefWeek.nextWeekStart(LocalDate.of(2026, 11, d))).as("11/" + d).isEqualTo(nextMonday);
        }
    }

    @Test
    @DisplayName("대상 주: 연말·연초와 윤년 경계")
    void nextWeekStart_boundaries() {
        assertThat(BriefWeek.nextWeekStart(LocalDate.of(2026, 12, 27))).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(BriefWeek.nextWeekStart(LocalDate.of(2026, 12, 31))).isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(BriefWeek.nextWeekStart(LocalDate.of(2028, 2, 27))).isEqualTo(LocalDate.of(2028, 2, 28));
        assertThat(BriefWeek.nextWeekStart(LocalDate.of(2028, 2, 28)).getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
    }

    @Test
    @DisplayName("주의 끝은 시작 + 6일(일요일)")
    void weekEnd() {
        assertThat(BriefWeek.weekEnd(LocalDate.of(2026, 11, 9))).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(BriefWeek.weekEnd(LocalDate.of(2026, 12, 28))).isEqualTo(LocalDate.of(2027, 1, 3));
    }

    // ---------------------------------------------------------------- 메시지

    @Test
    @DisplayName("메시지 형식: 제목 줄, 날짜별 목록(시간 있는 일정은 시간 먼저), 총 건수")
    void format() {
        List<String> parts = BriefMessageBuilder.build(LocalDate.of(2026, 11, 9), List.of(
                plan(2026, 11, 9, "치과 예약", "09:30"),
                plan(2026, 11, 9, "점심", null),
                plan(2026, 11, 11, "회의", "14:00")));

        assertThat(parts).hasSize(1);
        assertThat(parts.get(0)).isEqualTo(String.join("\n",
                "주간 일정 브리핑 (11/9 ~ 11/15)",
                "",
                "11/9(월)",
                " - 09:30 치과 예약",
                " - 점심",
                "",
                "11/11(수)",
                " - 14:00 회의",
                "",
                "총 3건"));
    }

    @Test
    @DisplayName("일정이 없으면 빈 목록(발송하지 않음)")
    void noPlans() {
        assertThat(BriefMessageBuilder.build(LocalDate.of(2026, 11, 9), List.of())).isEmpty();
    }

    @Test
    @DisplayName("그 주 밖의 일정과 달력에 없는 날짜(2/31, 0일)의 일정은 넣지 않는다")
    void filtersOutOfWeekAndInvalidDates() {
        List<String> parts = BriefMessageBuilder.build(LocalDate.of(2026, 2, 23), List.of(
                plan(2026, 2, 22, "전 주 일요일", null),
                plan(2026, 2, 31, "없는 날짜", null),
                plan(2026, 2, 0, "영일", null),
                plan(2026, 13, 1, "없는 달", null),
                plan(2026, 3, 1, "그 주 일요일", null),
                plan(2026, 3, 2, "다음 주 월요일", null)));

        assertThat(parts).hasSize(1);
        assertThat(parts.get(0)).contains("3/1(일)", "그 주 일요일", "총 1건")
                .doesNotContain("전 주", "없는", "영일", "다음 주");
    }

    @Test
    @DisplayName("전부 걸러지면 빈 목록")
    void allFilteredOut() {
        assertThat(BriefMessageBuilder.build(LocalDate.of(2026, 2, 23), List.of(plan(2026, 2, 31, "x", null)))).isEmpty();
    }

    @Test
    @DisplayName("연도를 넘는 주(2026-12-28 ~ 2027-01-03)도 날짜 순으로 나온다")
    void crossYear() {
        List<String> parts = BriefMessageBuilder.build(LocalDate.of(2026, 12, 28), List.of(
                plan(2027, 1, 1, "새해", null),
                plan(2026, 12, 31, "송년", "20:00"),
                plan(2027, 1, 4, "다음 주", null)));

        assertThat(parts).hasSize(1);
        String text = parts.get(0);
        assertThat(text).startsWith("주간 일정 브리핑 (12/28 ~ 1/3)");
        assertThat(text.indexOf("12/31(목)")).isLessThan(text.indexOf("1/1(금)"));
        assertThat(text).contains(" - 20:00 송년", " - 새해", "총 2건").doesNotContain("다음 주");
    }

    @Test
    @DisplayName("입력 순서가 뒤섞여도 날짜 → 시작 시간(없으면 뒤) 순으로 정렬한다")
    void sorting() {
        List<String> parts = BriefMessageBuilder.build(LocalDate.of(2026, 11, 9), List.of(
                plan(2026, 11, 10, "b", null),
                plan(2026, 11, 10, "a", "08:00"),
                plan(2026, 11, 9, "c", null)));

        String text = parts.get(0);
        assertThat(text.indexOf("c")).isLessThan(text.indexOf(" - 08:00 a"));
        assertThat(text.indexOf(" - 08:00 a")).isLessThan(text.indexOf(" - b"));
    }

    @Test
    @DisplayName("제목 정리: 줄바꿈·탭·연속 공백은 공백 하나로, 빈 제목은 안내 문구, 100자 넘으면 줄임")
    void titleCleaning() {
        assertThat(BriefMessageBuilder.cleanTitle("줄\n바꿈\t탭   공백")).isEqualTo("줄 바꿈 탭 공백");
        assertThat(BriefMessageBuilder.cleanTitle(null)).isEqualTo("(제목 없음)");
        assertThat(BriefMessageBuilder.cleanTitle("  \n ")).isEqualTo("(제목 없음)");
        assertThat(BriefMessageBuilder.cleanTitle("a".repeat(100))).hasSize(100);
        assertThat(BriefMessageBuilder.cleanTitle("a".repeat(101))).isEqualTo("a".repeat(100) + "…");
    }

    @Test
    @DisplayName("특수문자(* _ [ ] < >)는 그대로 둔다(parse_mode를 쓰지 않으므로 해석되지 않는다)")
    void specialCharactersUntouched() {
        List<String> parts = BriefMessageBuilder.build(LocalDate.of(2026, 11, 9), List.of(plan(2026, 11, 9, "*굵게* _기울임_ [링크](x) <b>", null)));

        assertThat(parts.get(0)).contains(" - *굵게* _기울임_ [링크](x) <b>");
    }

    @Test
    @DisplayName("4000자를 넘으면 조각으로 나누고, 각 조각은 4000자 이하이며 일정은 하나도 빠지지 않는다")
    void splitsLongMessages() {
        List<BriefPlanRow> plans = new ArrayList<>();
        for (int i = 1; i <= 300; i++) {
            String number = String.format("%03d", i);
            plans.add(plan(2026, 11, 9, number + "x".repeat(97), null));       // 정확히 100자
        }

        List<String> parts = BriefMessageBuilder.build(LocalDate.of(2026, 11, 9), plans);

        assertThat(parts.size()).isGreaterThan(5);
        assertThat(parts).allSatisfy(p -> assertThat(p.length()).isLessThanOrEqualTo(BriefMessageBuilder.MAX_LENGTH));

        String all = String.join("\n", parts);
        for (int i = 1; i <= 300; i++) {
            assertThat(all).contains(" - " + String.format("%03d", i) + "x");
        }
        assertThat(all.split("\n", -1)).filteredOn(l -> l.startsWith(" - ")).hasSize(300);

        // 하루 목록 중간에서 나뉜 다음 조각은 날짜를 다시 적어 준다
        for (int i = 1; i < parts.size(); i++) {
            assertThat(parts.get(i)).startsWith("11/9(월) (계속)");
        }
        assertThat(parts.get(parts.size() - 1)).endsWith("총 300건");
        assertThat(parts.get(0)).startsWith("주간 일정 브리핑 (11/9 ~ 11/15)");
    }

    @Test
    @DisplayName("한 조각으로 충분하면 나누지 않는다(경계: 조각이 정확히 4000자 이하)")
    void noSplitWhenShort() {
        List<BriefPlanRow> plans = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            plans.add(plan(2026, 11, 9 + (i % 7), "일정 " + i, null));
        }

        assertThat(BriefMessageBuilder.build(LocalDate.of(2026, 11, 9), plans)).hasSize(1);
    }

}
