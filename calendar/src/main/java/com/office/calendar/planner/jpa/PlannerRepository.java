package com.office.calendar.planner.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlannerRepository extends JpaRepository<PlannerEntity, Integer> {

    List<PlannerEntity> findByPlanYearAndPlanMonthAndPlanOwnerId(int planYear, int planMonth, String planOwnerId);

    // 일정 번호와 소유자 아이디가 모두 맞는 일정만 조회한다(본인 일정만 접근)
    PlannerEntity findByPlanNoAndPlanOwnerId(int planNo, String planOwnerId);

    boolean existsByPlanNoAndPlanOwnerId(int planNo, String planOwnerId);

    int deleteByPlanNoAndPlanOwnerId(int planNo, String planOwnerId);

}
