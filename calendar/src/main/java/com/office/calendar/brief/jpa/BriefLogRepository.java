package com.office.calendar.brief.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface BriefLogRepository extends JpaRepository<BriefLogEntity, Integer> {

    // 이 회원의 이 주 브리핑을 이미 처리했는지(중복 발송 방지)
    boolean existsByMemberNoAndWeekStart(int memberNo, LocalDate weekStart);

}
