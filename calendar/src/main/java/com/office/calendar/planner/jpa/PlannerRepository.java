package com.office.calendar.planner.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlannerRepository extends JpaRepository<PlannerEntity, Integer> {

    List<PlannerEntity> findByPlanYearAndPlanMonthAndPlanOwnerId(int planYear, int planMonth, String planOwnerId);

    PlannerEntity findByPlanNo(int planNo);

    int deleteByPlanNo(int planNo);

}
