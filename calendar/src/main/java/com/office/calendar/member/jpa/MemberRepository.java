package com.office.calendar.member.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<MemberEntity, Integer> {

    // 회원 ID 중복체크
    public boolean existsByMemId(String memId);

    // 회원 ID로 회원 조회
    public Optional<MemberEntity> findByMemId(String memId);

    // 회원 ID와 MAIL로 회원 조회(인증)
    public Optional<MemberEntity> findByMemIdAndMemMail(String memId, String memMail);

    // 해당 권한을 가진 회원이 있는지(예: 관리자 계정 시드 전 확인)
    public boolean existsByAuthorityEntity_AuthRoleName(String authRoleName);

}
