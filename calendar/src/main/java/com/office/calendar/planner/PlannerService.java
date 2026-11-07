package com.office.calendar.planner;

import com.office.calendar.planner.jpa.PlannerEntity;
import com.office.calendar.planner.jpa.PlannerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlannerService {

    final public int PLAN_REGISTE_SUCCESS = 1;
    final public int PLAN_REGISTE_FAIL = 0;

    final public int PLAN_REMOVE_SUCCESS = 1;
    final public int PLAN_REMOVE_FAIL = 0;

    final public int PLAN_MODIFY_SUCCESS = 1;
    final public int PLAN_MODIFY_FAIL = 0;

    final private PlannerRepository plannerRepository;

    // 일정 등록
    @Transactional
    public Map<String, Object> writePlan(PlannerDto plannerDto) {
        log.info("writePlan()");

        Map<String, Object> resultMap = new HashMap<>();

        int result = PLAN_REGISTE_FAIL;
        plannerDto.setOri_owner_id(plannerDto.getOwner_id());
        PlannerEntity savedPlannerEntity = plannerRepository.save(plannerDto.toEntity());
        if (savedPlannerEntity != null) {
            savedPlannerEntity.setPlanOriNo(savedPlannerEntity.getPlanNo());
            plannerRepository.save(savedPlannerEntity);

            log.info("INSERT NEW PLAN SUCCESS!!");
            result = PLAN_REGISTE_SUCCESS;

        } else {
            log.info("INSERT NEW PLAN FAIL!!");

        }

        resultMap.put("result", result);

        return resultMap;

    }

    public Map<String, Object> getPlans(int year, int month, String ownerId) {
        log.info("getPlans()");

        Map<String, Object> resultMap = new HashMap<>();

        List<PlannerEntity> plannerEntities =
                plannerRepository.findByPlanYearAndPlanMonthAndPlanOwnerId(year, month, ownerId);

        List<PlannerDto> plannerDtos = plannerEntities.stream()
                .map(PlannerEntity::toDto)
                .collect(Collectors.toList());

        resultMap.put("plans", plannerDtos);

        return resultMap;

    }

    public Map<String, Object> getPlan(int no) {
        log.info("getPlan()");

        Map<String, Object> resultMap = new HashMap<>();

        PlannerEntity plannerEntity = plannerRepository.findByPlanNo(no);

        resultMap.put("plan", plannerEntity != null ? plannerEntity.toDto() : null);

        return resultMap;

    }

    @Transactional
    public Map<String, Object> removePlan(int no) {
        log.info("removePlan()");

        Map<String, Object> resultMap = new HashMap<>();

        int result = plannerRepository.deleteByPlanNo(no);
        if (result > PLAN_REMOVE_FAIL) {
            log.info("REMOVE PLAN SUCCESS!!");

        } else {
            log.info("REMOVE PLAN FAIL!!");

        }

        resultMap.put("result", result);

        return resultMap;

    }

    @Transactional
    public Map<String, Object> modifyPlan(PlannerDto plannerDto) {
        log.info("modifyPlan()");

        Map<String, Object> resultMap = new HashMap<>();

        int result = PLAN_MODIFY_FAIL;

        PlannerEntity plannerEntity = plannerRepository.findByPlanNo(plannerDto.getNo());
        if (plannerEntity != null) {
            plannerEntity.setPlanYear(plannerDto.getYear());
            plannerEntity.setPlanMonth(plannerDto.getMonth());
            plannerEntity.setPlanDate(plannerDto.getDate());
            plannerEntity.setPlanTitle(plannerDto.getTitle());
            plannerEntity.setPlanBody(plannerDto.getBody());

            if (plannerDto.getImg_name() != null) {
                plannerEntity.setPlanImgName(plannerDto.getImg_name());
            }

            result = PLAN_MODIFY_SUCCESS;

        }

        resultMap.put("result", result);

        return resultMap;

    }
}
