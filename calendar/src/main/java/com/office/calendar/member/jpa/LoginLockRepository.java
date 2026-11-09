package com.office.calendar.member.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LoginLockRepository extends JpaRepository<LoginLockEntity, LoginLockId> {

    // 실패 횟수를 DB에서 바로 1 올린다(동시 요청이 겹쳐도 횟수가 덮어써지지 않게). 바뀐 행 수를 돌려준다(0이면 행이 없음)
    @Modifying
    @Query("update LoginLockEntity l set l.failCount = l.failCount + 1, l.updatedAt = :now "
            + "where l.loginId = :loginId and l.clientIp = :clientIp")
    int increaseFailCount(@Param("loginId") String loginId, @Param("clientIp") String clientIp,
                          @Param("now") LocalDateTime now);

    // 지정 횟수에 이른 행에 잠금 해제 시각을 정한다. 바뀐 행 수를 돌려준다
    @Modifying
    @Query("update LoginLockEntity l set l.lockedUntil = :lockedUntil "
            + "where l.loginId = :loginId and l.clientIp = :clientIp and l.failCount >= :maxFail")
    int lock(@Param("loginId") String loginId, @Param("clientIp") String clientIp,
             @Param("maxFail") int maxFail, @Param("lockedUntil") LocalDateTime lockedUntil);

    // 로그인 성공 시 기록 삭제
    @Modifying
    @Query("delete from LoginLockEntity l where l.loginId = :loginId and l.clientIp = :clientIp")
    int deleteByKey(@Param("loginId") String loginId, @Param("clientIp") String clientIp);

    // 오래된 미잠금 행 정리. 지운 행 수를 돌려준다
    @Modifying
    @Query("delete from LoginLockEntity l where l.updatedAt < :time and (l.lockedUntil is null or l.lockedUntil <= :now)")
    int deleteStale(@Param("time") LocalDateTime time, @Param("now") LocalDateTime now);

}
