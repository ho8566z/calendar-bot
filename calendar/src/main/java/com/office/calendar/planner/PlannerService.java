package com.office.calendar.planner;

import com.office.calendar.common.LikeKeyword;
import com.office.calendar.planner.jpa.PlannerEntity;
import com.office.calendar.planner.jpa.PlannerRepository;
import com.office.calendar.planner.mapper.PlannerMapper;
import com.office.calendar.planner.util.StartTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
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

    // 일정 정렬: 날짜 → 시작 시간(시간 없는 일정은 그날의 맨 뒤) → 번호
    private static final Comparator<PlannerEntity> PLAN_ORDER = Comparator
            .comparingInt(PlannerEntity::getPlanDate)
            .thenComparing(PlannerEntity::getPlanStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(PlannerEntity::getPlanNo);

    public static final int SEARCH_LIMIT = 100;     // 검색 결과 최대 건수

    final private PlannerRepository plannerRepository;
    final private PlannerMapper plannerMapper;

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
                .sorted(PLAN_ORDER)
                .map(PlannerEntity::toDto)
                .collect(Collectors.toList());

        resultMap.put("plans", plannerDtos);

        return resultMap;

    }

    // 일정 검색(plan-008): 본인 일정 중 제목·내용에 검색어가 들어간 것. 검색어가 비면 빈 목록(전체 목록을 내려주지 않는다)
    @Transactional(readOnly = true)
    public Map<String, Object> searchPlans(String keyword, String ownerId) {
        log.info("searchPlans()");

        Map<String, Object> resultMap = new HashMap<>();

        String likeKeyword = LikeKeyword.toPattern(keyword);
        List<PlannerDto> plans = likeKeyword == null
                ? List.of()
                : plannerMapper.searchPlans(ownerId, likeKeyword, SEARCH_LIMIT);

        resultMap.put("plans", plans);
        resultMap.put("limit", SEARCH_LIMIT);

        return resultMap;

    }

    // 일정 소유자 확인
    public boolean isOwner(int no, String ownerId) {
        log.info("isOwner()");

        return plannerRepository.existsByPlanNoAndPlanOwnerId(no, ownerId);

    }

    // 일정 1건 조회: 요청한 사람의 일정이 아니면 없는 일정(null)으로 돌려준다
    public Map<String, Object> getPlan(int no, String ownerId) {
        log.info("getPlan()");

        Map<String, Object> resultMap = new HashMap<>();

        PlannerEntity plannerEntity = plannerRepository.findByPlanNoAndPlanOwnerId(no, ownerId);

        resultMap.put("plan", plannerEntity != null ? plannerEntity.toDto() : null);

        return resultMap;

    }

    // 일정 삭제: 요청한 사람의 일정만 지운다(남의 일정이면 0건)
    @Transactional
    public Map<String, Object> removePlan(int no, String ownerId) {
        log.info("removePlan()");

        Map<String, Object> resultMap = new HashMap<>();

        int result = plannerRepository.deleteByPlanNoAndPlanOwnerId(no, ownerId);
        if (result > PLAN_REMOVE_FAIL) {
            log.info("REMOVE PLAN SUCCESS!!");

        } else {
            log.info("REMOVE PLAN FAIL!!");

        }

        resultMap.put("result", result);

        return resultMap;

    }

    // 일정 수정: 요청한 사람의 일정만 고친다(남의 일정이면 실패)
    @Transactional
    public Map<String, Object> modifyPlan(PlannerDto plannerDto, String ownerId) {
        log.info("modifyPlan()");

        Map<String, Object> resultMap = new HashMap<>();

        int result = PLAN_MODIFY_FAIL;

        PlannerEntity plannerEntity = plannerRepository.findByPlanNoAndPlanOwnerId(plannerDto.getNo(), ownerId);
        if (plannerEntity != null) {
            plannerEntity.setPlanYear(plannerDto.getYear());
            plannerEntity.setPlanMonth(plannerDto.getMonth());
            plannerEntity.setPlanDate(plannerDto.getDate());
            plannerEntity.setPlanTitle(plannerDto.getTitle());
            plannerEntity.setPlanBody(plannerDto.getBody());
            // 시작 시간은 선택이라 비우면 시간이 지워진다
            plannerEntity.setPlanStartTime(StartTimes.toLocalTime(plannerDto.getStart_time()));

            if (plannerDto.getImg_name() != null) {
                plannerEntity.setPlanImgName(plannerDto.getImg_name());
            }

            result = PLAN_MODIFY_SUCCESS;

        }

        resultMap.put("result", result);

        return resultMap;

    }
}
