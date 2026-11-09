package com.office.calendar.planner;

import com.office.calendar.planner.mapper.PlannerMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 월별 통계(plan-010): 0 채우기, 가장 많은 주, 범위 검증
@ExtendWith(MockitoExtension.class)
class PlannerServiceStatsTest {

    @Mock
    private PlannerMapper plannerMapper;

    @InjectMocks
    private PlannerService plannerService;

    private static PlannerStatRow row(int bucket, int cnt) {
        return new PlannerStatRow(bucket, cnt);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> busiest(Map<String, Object> stats) {
        return (Map<String, Object>) stats.get("busiest_week");
    }

    @Test
    @DisplayName("일정이 없는 요일·주는 0으로 채우고 가장 많은 주는 null이다")
    void emptyMonth() {
        when(plannerMapper.countByWeekday("alice", 2026, 11)).thenReturn(List.of());
        when(plannerMapper.countByWeek("alice", 2026, 11)).thenReturn(List.of());

        Map<String, Object> stats = plannerService.getMonthlyStats(2026, 11, "alice");

        assertThat(stats.get("total")).isEqualTo(0);
        assertThat(stats.get("by_weekday")).isEqualTo(List.of(0, 0, 0, 0, 0, 0, 0));
        assertThat(stats.get("by_week")).isEqualTo(List.of(0, 0, 0, 0, 0));
        assertThat(busiest(stats)).isNull();
    }

    @Test
    @DisplayName("요일별은 일요일부터 7칸, 총계는 요일별의 합이다")
    void weekdayAndTotal() {
        // 2026-11: 1일이 일요일. 일요일 2건, 수요일 1건, 토요일 4건
        when(plannerMapper.countByWeekday("alice", 2026, 11)).thenReturn(List.of(row(1, 2), row(4, 1), row(7, 4)));
        when(plannerMapper.countByWeek("alice", 2026, 11)).thenReturn(List.of(row(1, 3), row(3, 4)));

        Map<String, Object> stats = plannerService.getMonthlyStats(2026, 11, "alice");

        assertThat(stats.get("by_weekday")).isEqualTo(List.of(2, 0, 0, 1, 0, 0, 4));
        assertThat(stats.get("total")).isEqualTo(7);
        assertThat(stats.get("by_week")).isEqualTo(List.of(3, 0, 4, 0, 0));
        assertThat(stats.get("year")).isEqualTo(2026);
        assertThat(stats.get("month")).isEqualTo(11);
    }

    @Test
    @DisplayName("가장 많은 주와 그 주의 날짜 범위(2026-11 3주차 = 15일~21일)")
    void busiestWeekWithDateRange() {
        when(plannerMapper.countByWeekday("alice", 2026, 11)).thenReturn(List.of(row(1, 7)));
        when(plannerMapper.countByWeek("alice", 2026, 11)).thenReturn(List.of(row(1, 3), row(3, 4)));

        Map<String, Object> busiest = busiest(plannerService.getMonthlyStats(2026, 11, "alice"));

        assertThat(busiest).containsEntry("week", 3).containsEntry("count", 4)
                .containsEntry("start_date", 15).containsEntry("end_date", 21);
    }

    @Test
    @DisplayName("동률이면 가장 이른 주를 고른다")
    void tieChoosesEarliestWeek() {
        when(plannerMapper.countByWeekday("alice", 2026, 11)).thenReturn(List.of(row(1, 6)));
        when(plannerMapper.countByWeek("alice", 2026, 11)).thenReturn(List.of(row(2, 3), row(4, 3), row(5, 3)));

        Map<String, Object> busiest = busiest(plannerService.getMonthlyStats(2026, 11, "alice"));

        assertThat(busiest).containsEntry("week", 2).containsEntry("count", 3);
    }

    @Test
    @DisplayName("첫 주와 마지막 주의 날짜 범위는 그 달 안으로 잘린다(2026-10: 1일이 목요일, 31일이 토요일)")
    void partialWeeksAreClipped() {
        when(plannerMapper.countByWeekday("alice", 2026, 10)).thenReturn(List.of(row(5, 1)));
        when(plannerMapper.countByWeek("alice", 2026, 10)).thenReturn(List.of(row(1, 1)));
        Map<String, Object> first = busiest(plannerService.getMonthlyStats(2026, 10, "alice"));
        assertThat(first).containsEntry("week", 1).containsEntry("start_date", 1).containsEntry("end_date", 3);

        when(plannerMapper.countByWeek("alice", 2026, 10)).thenReturn(List.of(row(5, 1)));
        Map<String, Object> last = busiest(plannerService.getMonthlyStats(2026, 10, "alice"));
        assertThat(last).containsEntry("week", 5).containsEntry("start_date", 25).containsEntry("end_date", 31);
    }

    @Test
    @DisplayName("주 수는 달력의 행 수와 같다: 4주(2026-02), 5주(2026-11), 6주(2026-05)")
    void weekCountMatchesCalendarRows() {
        for (int[] ym : new int[][]{{2026, 2, 4}, {2026, 11, 5}, {2026, 5, 6}}) {
            when(plannerMapper.countByWeekday("alice", ym[0], ym[1])).thenReturn(List.of());
            when(plannerMapper.countByWeek("alice", ym[0], ym[1])).thenReturn(List.of());

            Map<String, Object> stats = plannerService.getMonthlyStats(ym[0], ym[1], "alice");

            assertThat((List<?>) stats.get("by_week")).as(ym[0] + "-" + ym[1]).hasSize(ym[2]);
        }
    }

    @Test
    @DisplayName("범위를 벗어난 주·요일 번호가 와도 오류 없이 무시한다")
    void ignoresOutOfRangeBuckets() {
        when(plannerMapper.countByWeekday("alice", 2026, 2)).thenReturn(List.of(row(0, 9), row(8, 9), row(1, 1)));
        when(plannerMapper.countByWeek("alice", 2026, 2)).thenReturn(List.of(row(0, 9), row(5, 9), row(1, 1)));

        Map<String, Object> stats = plannerService.getMonthlyStats(2026, 2, "alice");

        assertThat(stats.get("by_weekday")).isEqualTo(List.of(1, 0, 0, 0, 0, 0, 0));
        assertThat(stats.get("by_week")).isEqualTo(List.of(1, 0, 0, 0));
    }

    @Test
    @DisplayName("통계 범위는 2025.1 ~ 2030.12이고 밖이면 조회하지 않고 예외를 던진다")
    void periodValidation() {
        assertThat(PlannerService.isValidStatsPeriod(2025, 1)).isTrue();
        assertThat(PlannerService.isValidStatsPeriod(2030, 12)).isTrue();
        assertThat(PlannerService.isValidStatsPeriod(2024, 12)).isFalse();
        assertThat(PlannerService.isValidStatsPeriod(2031, 1)).isFalse();
        assertThat(PlannerService.isValidStatsPeriod(2026, 0)).isFalse();
        assertThat(PlannerService.isValidStatsPeriod(2026, 13)).isFalse();

        assertThatThrownBy(() -> plannerService.getMonthlyStats(2031, 1, "alice")).isInstanceOf(IllegalArgumentException.class);
        verify(plannerMapper, never()).countByWeekday(anyString(), anyInt(), anyInt());
    }

}
