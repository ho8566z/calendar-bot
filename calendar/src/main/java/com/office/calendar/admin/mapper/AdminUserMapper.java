package com.office.calendar.admin.mapper;

import com.office.calendar.admin.AdminUserDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 관리자 사용자 목록 조회(동적 조건·조인이라 MyBatis로 처리). SQL은 admin-user-mapper.xml
@Mapper
public interface AdminUserMapper {

    // keyword가 null이면 전체, 있으면 아이디에 포함된 회원만. offset부터 size건
    List<AdminUserDto> selectUsers(@Param("keyword") String keyword,
                                   @Param("size") int size,
                                   @Param("offset") int offset);

    // 같은 조건의 전체 건수
    int countUsers(@Param("keyword") String keyword);

}
