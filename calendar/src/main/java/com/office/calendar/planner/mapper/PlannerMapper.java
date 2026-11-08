package com.office.calendar.planner.mapper;

import com.office.calendar.planner.PlannerDto;
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

}
