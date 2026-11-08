package com.office.calendar.planner;

import com.office.calendar.planner.jpa.PlannerEntity;
import com.office.calendar.planner.jpa.PlannerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 일정 소유자 검증(plan-009): 번호만 알아도 남의 일정에 접근할 수 없어야 한다
@ExtendWith(MockitoExtension.class)
class PlannerServiceOwnerTest {

    @Mock
    private PlannerRepository plannerRepository;

    @InjectMocks
    private PlannerService plannerService;

    private PlannerEntity planOf(String ownerId) {
        return PlannerEntity.builder()
                .planNo(1)
                .planOwnerId(ownerId)
                .planYear(2026).planMonth(11).planDate(8)
                .planTitle("원래 제목").planBody("원래 내용")
                .build();
    }

    @Test
    @DisplayName("본인 일정이면 조회된다")
    void getPlan_owner() {
        when(plannerRepository.findByPlanNoAndPlanOwnerId(1, "alice")).thenReturn(planOf("alice"));

        Map<String, Object> result = plannerService.getPlan(1, "alice");

        assertThat(result.get("plan")).isNotNull();
    }

    @Test
    @DisplayName("남의 일정 번호로 조회하면 없는 일정과 같은 결과다")
    void getPlan_otherUser() {
        when(plannerRepository.findByPlanNoAndPlanOwnerId(1, "mallory")).thenReturn(null);

        Map<String, Object> result = plannerService.getPlan(1, "mallory");

        assertThat(result.get("plan")).isNull();
        verify(plannerRepository).findByPlanNoAndPlanOwnerId(1, "mallory");
    }

    @Test
    @DisplayName("본인 일정이면 삭제된다")
    void removePlan_owner() {
        when(plannerRepository.deleteByPlanNoAndPlanOwnerId(1, "alice")).thenReturn(1);

        Map<String, Object> result = plannerService.removePlan(1, "alice");

        assertThat(result.get("result")).isEqualTo(plannerService.PLAN_REMOVE_SUCCESS);
    }

    @Test
    @DisplayName("남의 일정은 삭제되지 않는다(0건)")
    void removePlan_otherUser() {
        when(plannerRepository.deleteByPlanNoAndPlanOwnerId(1, "mallory")).thenReturn(0);

        Map<String, Object> result = plannerService.removePlan(1, "mallory");

        assertThat(result.get("result")).isEqualTo(plannerService.PLAN_REMOVE_FAIL);
    }

    @Test
    @DisplayName("본인 일정이면 내용이 수정된다")
    void modifyPlan_owner() {
        PlannerEntity entity = planOf("alice");
        when(plannerRepository.findByPlanNoAndPlanOwnerId(1, "alice")).thenReturn(entity);
        PlannerDto dto = PlannerDto.builder().no(1).year(2026).month(11).date(9)
                .title("새 제목").body("새 내용").build();

        Map<String, Object> result = plannerService.modifyPlan(dto, "alice");

        assertThat(result.get("result")).isEqualTo(plannerService.PLAN_MODIFY_SUCCESS);
        assertThat(entity.getPlanTitle()).isEqualTo("새 제목");
        assertThat(entity.getPlanDate()).isEqualTo(9);
    }

    @Test
    @DisplayName("남의 일정은 수정되지 않는다")
    void modifyPlan_otherUser() {
        when(plannerRepository.findByPlanNoAndPlanOwnerId(1, "mallory")).thenReturn(null);
        PlannerDto dto = PlannerDto.builder().no(1).year(2026).month(11).date(9)
                .title("바꿔치기").body("바꿔치기").build();

        Map<String, Object> result = plannerService.modifyPlan(dto, "mallory");

        assertThat(result.get("result")).isEqualTo(plannerService.PLAN_MODIFY_FAIL);
    }

    @Test
    @DisplayName("소유자 확인은 번호와 아이디를 함께 넘긴다")
    void isOwner() {
        when(plannerRepository.existsByPlanNoAndPlanOwnerId(1, "alice")).thenReturn(true);
        when(plannerRepository.existsByPlanNoAndPlanOwnerId(1, "mallory")).thenReturn(false);

        assertThat(plannerService.isOwner(1, "alice")).isTrue();
        assertThat(plannerService.isOwner(1, "mallory")).isFalse();
    }

}
