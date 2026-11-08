package com.office.calendar.member.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface TelegramLinkRepository extends JpaRepository<TelegramLinkEntity, String> {

    // 회원의 미사용 코드 삭제(새 코드를 발급하거나 연동 해제할 때). 지운 행 수를 돌려준다.
    // 파생 삭제(deleteBy...)는 같은 트랜잭션에서 새 코드 INSERT보다 늦게 실행되어 MEMBER_NO 유니크에 걸리므로 바로 실행되는 쿼리로 지운다.
    @Modifying
    @Query("delete from TelegramLinkEntity l where l.memberNo = :memberNo")
    int deleteByMemberNo(@Param("memberNo") int memberNo);

    // 만료된 코드 정리. 지운 행 수를 돌려준다
    @Modifying
    @Query("delete from TelegramLinkEntity l where l.expiresAt <= :time")
    int deleteExpired(@Param("time") LocalDateTime time);

}
