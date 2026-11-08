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

import java.time.YearMonth;
import java.util.Arrays;
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

    // 월별 통계 조회 범위(달력 화면의 년도 선택 범위와 같다)
    public static final int STATS_MIN_YEAR = 2025;
    public static final int STATS_MAX_YEAR = 2030;

    public static boolean isValidStatsPeriod(int year, int month) {
        return year >= STATS_MIN_YEAR && year <= STATS_MAX_YEAR && month >= 1 && month <= 12;

    }

    // 월별 통계(plan-010): 본인 일정의 총 개수, 요일별(일~토) 개수, 주별 개수, 일정이 가장 많은 주.
    // 주는 화면 달력의 행(일요일 시작)과 같고, 가장 많은 주가 여러 개면 가장 이른 주를 고른다. 일정이 없으면 busiest_week는 null.
    @Transactional(readOnly = true)
    public Map<String, Object> getMonthlyStats(int year, int month, String ownerId) {
        log.info("getMonthlyStats()");

        if (!isValidStatsPeriod(year, month)) {
            throw new IllegalArgumentException("통계 범위는 " + STATS_MIN_YEAR + "년 1월 ~ " + STATS_MAX_YEAR + "년 12월입니다.");
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        int lastDay = yearMonth.lengthOfMonth();
        int offset = yearMonth.atDay(1).getDayOfWeek().getValue() % 7;      // 1일의 요일(일요일 0 ~ 토요일 6)
        int weekCount = (offset + lastDay + 6) / 7;                          // 달력의 행 수(4 ~ 6)

        int[] byWeekday = new int[7];
        for (PlannerStatRow row : plannerMapper.countByWeekday(ownerId, year, month)) {
            if (row.getBucket() >= 1 && row.getBucket() <= 7) {
                byWeekday[row.getBucket() - 1] = row.getCnt();
            }
        }

        int[] byWeek = new int[weekCount];
        for (PlannerStatRow row : plannerMapper.countByWeek(ownerId, year, month)) {
            if (row.getBucket() >= 1 && row.getBucket() <= weekCount) {
                byWeek[row.getBucket() - 1] = row.getCnt();
            }
        }

        int total = Arrays.stream(byWeekday).sum();

        Map<String, Object> busiestWeek = null;
        int busiestIndex = -1;
        for (int i = 0; i < weekCount; i++) {
            if (byWeek[i] > 0 && (busiestIndex < 0 || byWeek[i] > byWeek[busiestIndex])) {      // 같으면 앞선 주를 유지
                busiestIndex = i;
            }
        }
        if (busiestIndex >= 0) {
            busiestWeek = new HashMap<>();
            busiestWeek.put("week", busiestIndex + 1);
            busiestWeek.put("count", byWeek[busiestIndex]);
            busiestWeek.put("start_date", Math.max(1, busiestIndex * 7 - offset + 1));
            busiestWeek.put("end_date", Math.min(lastDay, (busiestIndex + 1) * 7 - offset));
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("year", year);
        resultMap.put("month", month);
        resultMap.put("total", total);
        resultMap.put("by_weekday", Arrays.stream(byWeekday).boxed().collect(Collectors.toList()));
        resultMap.put("by_week", Arrays.stream(byWeek).boxed().collect(Collectors.toList()));
        resultMap.put("busiest_week", busiestWeek);

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
