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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 일정 검색(plan-008): 검색어 처리와 본인 범위
@ExtendWith(MockitoExtension.class)
class PlannerServiceSearchTest {

    @Mock
    private PlannerMapper plannerMapper;

    @InjectMocks
    private PlannerService plannerService;

    @Test
    @DisplayName("검색어는 공백을 지우고 이스케이프한 뒤 요청한 사람의 아이디와 상한과 함께 매퍼에 넘긴다")
    void passesOwnerAndEscapedKeyword() {
        PlannerDto found = PlannerDto.builder().no(1).title("100% 치과").build();
        when(plannerMapper.searchPlans("alice", "100!%", PlannerService.SEARCH_LIMIT)).thenReturn(List.of(found));

        Map<String, Object> result = plannerService.searchPlans("  100%  ", "alice");

        assertThat(result.get("plans")).isEqualTo(List.of(found));
        assertThat(result.get("limit")).isEqualTo(PlannerService.SEARCH_LIMIT);
    }

    @Test
    @DisplayName("검색어가 null·빈 값·공백이면 DB를 조회하지 않고 빈 목록을 돌려준다(전체 목록을 내리지 않음)")
    void blankKeywordReturnsEmpty() {
        for (String blank : new String[]{null, "", "   "}) {
            Map<String, Object> result = plannerService.searchPlans(blank, "alice");

            assertThat((List<?>) result.get("plans")).isEmpty();
        }

        verify(plannerMapper, never()).searchPlans(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("검색 건수 상한은 100건이다")
    void limitIs100() {
        assertThat(PlannerService.SEARCH_LIMIT).isEqualTo(100);
    }

}
