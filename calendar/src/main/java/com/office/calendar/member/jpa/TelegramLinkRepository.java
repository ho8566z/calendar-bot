package com.office.calendar.member.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface TelegramLinkRepository extends JpaRepository<TelegramLinkEntity, String> {

    // 회원의 미사용 코드 삭제(새 코드를 발급하거나 연동이 끝났을 때). 지운 행 수를 돌려준다
    int deleteByMemberNo(int memberNo);

    // 만료된 코드 정리. 지운 행 수를 돌려준다
    int deleteByExpiresAtBefore(LocalDateTime time);

}
