package com.office.calendar.planner.mapper;

import com.office.calendar.planner.PlannerDto;
import com.office.calendar.planner.PlannerStatRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 일정 검색·통계처럼 동적 조건·집계가 필요한 조회는 MyBatis로 처리한다(쓰기와 단순 조회는 JPA). SQL은 planner-mapper.xml
@Mapper
public interface PlannerMapper {

    // ownerId의 일정 중 제목 또는 내용에 keyword가 들어간 것을 최신 날짜순으로 최대 limit건.
    // keyword는 LikeKeyword로 이스케이프한 값이며 null이면 검색 조건 없이 ownerId의 일정 전체가 대상이다.
    List<PlannerDto> searchPlans(@Param("ownerId") String ownerId,
                                 @Param("keyword") String keyword,
                                 @Param("limit") int limit);

    // ownerId의 year년 month월 일정 수를 요일별로 센다. bucket: 1(일요일) ~ 7(토요일). 일정이 없는 요일은 행이 없다.
    List<PlannerStatRow> countByWeekday(@Param("ownerId") String ownerId,
                                        @Param("year") int year,
                                        @Param("month") int month);

    // ownerId의 year년 month월 일정 수를 주(달력의 행, 일요일 시작)별로 센다. bucket: 1 ~ 6. 일정이 없는 주는 행이 없다.
    List<PlannerStatRow> countByWeek(@Param("ownerId") String ownerId,
                                     @Param("year") int year,
                                     @Param("month") int month);

}
