package com.office.calendar.brief.mapper;

import com.office.calendar.brief.BriefPlanRow;
import com.office.calendar.brief.BriefTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

// 브리핑 대상 회원과 주간 일정 조회(조인·범위 조건이라 MyBatis). SQL은 brief-mapper.xml
@Mapper
public interface BriefMapper {

    // weekStart 주의 브리핑을 받을 회원: 수신을 켰고 텔레그램이 연동된 USER 중 그 주에 이미 처리한(BRIEF_LOG 행이 있는) 회원은 뺀다
    List<BriefTarget> selectTargets(@Param("weekStart") LocalDate weekStart);

    // ownerId의 일정 중 fromKey ~ toKey(yyyymmdd 정수, 양끝 포함) 범위를 날짜·시작 시간(없으면 뒤)·번호 순으로
    List<BriefPlanRow> selectPlans(@Param("ownerId") String ownerId,
                                   @Param("fromKey") int fromKey,
                                   @Param("toKey") int toKey);

}
