package com.office.calendar.planner;

import com.office.calendar.planner.jpa.PlannerEntity;
import com.office.calendar.planner.jpa.PlannerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// 시작 시간(plan-007): 저장·변환·정렬
@ExtendWith(MockitoExtension.class)
class PlannerServiceTimeTest {

    @Mock
    private PlannerRepository plannerRepository;

    @InjectMocks
    private PlannerService plannerService;

    private PlannerEntity plan(int no, int date, LocalTime time) {
        return PlannerEntity.builder()
                .planNo(no).planOwnerId("alice")
                .planYear(2026).planMonth(11).planDate(date)
                .planTitle("t" + no).planBody("b")
                .planStartTime(time)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<PlannerDto> getPlans() {
        Map<String, Object> result = plannerService.getPlans(2026, 11, "alice");
        return (List<PlannerDto>) result.get("plans");
    }

    @Test
    @DisplayName("월별 일정은 날짜 → 시작 시간 → 번호 순이고, 시간 없는 일정은 그날의 맨 뒤다")
    void getPlans_sortedByDateThenTime() {
        when(plannerRepository.findByPlanYearAndPlanMonthAndPlanOwnerId(2026, 11, "alice")).thenReturn(List.of(
                plan(3, 9, null),
                plan(2, 8, LocalTime.of(14, 0)),
                plan(5, 8, null),
                plan(1, 8, LocalTime.of(9, 0)),
                plan(4, 8, null)));

        List<PlannerDto> plans = getPlans();

        assertThat(plans).extracting(PlannerDto::getNo).containsExactly(1, 2, 4, 5, 3);
        assertThat(plans.get(0).getStart_time()).isEqualTo("09:00");
        assertThat(plans.get(2).getStart_time()).as("시간 없는 일정").isNull();
    }

    @Test
    @DisplayName("수정하면 시작 시간이 저장된다")
    void modify_setsStartTime() {
        PlannerEntity entity = plan(1, 8, null);
        when(plannerRepository.findByPlanNoAndPlanOwnerId(1, "alice")).thenReturn(entity);
        PlannerDto dto = PlannerDto.builder().no(1).year(2026).month(11).date(8)
                .title("t").body("b").start_time("10:30").build();

        plannerService.modifyPlan(dto, "alice");

        assertThat(entity.getPlanStartTime()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    @DisplayName("수정에서 시작 시간을 비우면 기존 시간이 지워진다")
    void modify_clearsStartTime() {
        PlannerEntity entity = plan(1, 8, LocalTime.of(9, 0));
        when(plannerRepository.findByPlanNoAndPlanOwnerId(1, "alice")).thenReturn(entity);
        PlannerDto dto = PlannerDto.builder().no(1).year(2026).month(11).date(8)
                .title("t").body("b").start_time(null).build();

        plannerService.modifyPlan(dto, "alice");

        assertThat(entity.getPlanStartTime()).isNull();
    }

    @Test
    @DisplayName("DTO와 엔티티 사이에서 시작 시간이 HH:mm으로 오간다")
    void dtoEntityMapping() {
        PlannerDto dto = PlannerDto.builder().start_time("09:05").build();
        assertThat(dto.toEntity().getPlanStartTime()).isEqualTo(LocalTime.of(9, 5));

        PlannerEntity entity = plan(1, 8, LocalTime.of(9, 5));
        assertThat(entity.toDto().getStart_time()).isEqualTo("09:05");

        assertThat(PlannerDto.builder().build().toEntity().getPlanStartTime()).isNull();
        assertThat(plan(2, 8, null).toDto().getStart_time()).isNull();
    }

}
