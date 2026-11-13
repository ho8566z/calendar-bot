package com.office.calendar.brief;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 주간 일정 목록을 텔레그램 일반 텍스트 메시지로 만든다(parse_mode 없음, 메시지 하나는 4096자 이하).
// 일정이 없으면 빈 목록을 돌려준다(발송하지 않음, brf-001). 날짜가 그 주 밖이거나 달력에 없는 날짜(2/31 등)인 일정은 뺀다.
public final class BriefMessageBuilder {

    // 텔레그램 메시지 한계는 4096자. 여유를 두고 4000자에서 나눈다
    public static final int MAX_LENGTH = 4000;
    static final int TITLE_MAX = 100;

    private static final String[] DAY_NAMES = {"월", "화", "수", "목", "금", "토", "일"};

    private BriefMessageBuilder() {
    }

    // 메시지 조각들(대부분 1개). 일정이 없으면 빈 목록
    public static List<String> build(LocalDate weekStart, List<BriefPlanRow> plans) {
        LocalDate weekEnd = BriefWeek.weekEnd(weekStart);

        List<Entry> entries = new ArrayList<>();
        for (BriefPlanRow plan : plans) {
            LocalDate date = toDate(plan);
            if (date != null && !date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
                entries.add(new Entry(date, plan.startTime(), cleanTitle(plan.title())));
            }
        }
        if (entries.isEmpty()) {
            return List.of();
        }

        // 날짜 → 시작 시간(없으면 그날 맨 뒤). 같으면 조회 순서를 유지한다(정렬은 안정적이다)
        entries.sort(Comparator.<Entry, LocalDate>comparing(e -> e.date)
                .thenComparing(e -> e.time, Comparator.nullsLast(Comparator.naturalOrder())));

        Chunker chunker = new Chunker();
        chunker.line("주간 일정 브리핑 (" + shortDate(weekStart) + " ~ " + shortDate(weekEnd) + ")");
        chunker.line("");

        LocalDate currentDay = null;
        for (Entry e : entries) {
            if (!e.date.equals(currentDay)) {
                if (currentDay != null) {
                    chunker.line("");
                }
                currentDay = e.date;
                chunker.dayHeader(dayLabel(currentDay));
            }
            chunker.item(dayLabel(currentDay), (e.time == null ? "" : e.time + " ") + e.title);
        }

        chunker.line("");
        chunker.line("총 " + entries.size() + "건");

        return chunker.finish();

    }

    private static LocalDate toDate(BriefPlanRow plan) {
        try {
            return LocalDate.of(plan.year(), plan.month(), plan.date());
        } catch (DateTimeException e) {
            return null;        // 달력에 없는 날짜
        }
    }

    // 줄바꿈·탭을 공백으로 바꾸고 길이를 줄인다. 비었으면 안내 문구
    static String cleanTitle(String title) {
        String t = title == null ? "" : title.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) {
            return "(제목 없음)";
        }
        return t.length() > TITLE_MAX ? t.substring(0, TITLE_MAX) + "…" : t;
    }

    static String shortDate(LocalDate d) {
        return d.getMonthValue() + "/" + d.getDayOfMonth();
    }

    static String dayLabel(LocalDate d) {
        return shortDate(d) + "(" + DAY_NAMES[d.getDayOfWeek().getValue() - 1] + ")";
    }

    private record Entry(LocalDate date, String time, String title) {
    }

    // 줄을 모아 MAX_LENGTH를 넘지 않게 조각으로 나눈다. 하루 목록 중간에서 나뉘면 다음 조각 머리에 날짜를 다시 적는다.
    private static final class Chunker {

        private final List<String> chunks = new ArrayList<>();
        private StringBuilder current = new StringBuilder();

        void line(String text) {
            append(text);
        }

        void dayHeader(String label) {
            append(label);
        }

        void item(String dayLabel, String text) {
            String line = " - " + text;
            if (wouldOverflow(line)) {
                flush();
                current.append(dayLabel).append(" (계속)");
            }
            append(line);
        }

        private void append(String text) {
            if (wouldOverflow(text)) {
                flush();
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(text);
        }

        private boolean wouldOverflow(String text) {
            return current.length() + 1 + text.length() > MAX_LENGTH && current.length() > 0;
        }

        private void flush() {
            String done = current.toString().stripTrailing();
            if (!done.isEmpty()) {
                chunks.add(done);
            }
            current = new StringBuilder();
        }

        List<String> finish() {
            flush();
            return chunks;
        }

    }

}
