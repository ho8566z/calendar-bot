package com.office.calendar.admin;

import com.office.calendar.member.jpa.AuthorityEntity;
import com.office.calendar.member.jpa.AuthorityRepository;
import com.office.calendar.member.jpa.MemberEntity;
import com.office.calendar.member.jpa.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

// 사용자 승인·승인 취소(adm-002). 쓰기는 JPA로 처리한다.
// 승인은 PRE_USER만, 승인 취소는 USER만 대상이라 ADMIN(과 관리자 본인)은 상태 검사에서 자동으로 걸러진다.
// 주의: 권한은 로그인할 때 세션에 담기므로 이미 로그인한 사용자에게는 재로그인 전까지 반영되지 않는다.
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserApprovalService {

    public static final String ROLE_PRE_USER = "PRE_USER";
    public static final String ROLE_USER = "USER";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    final private MemberRepository memberRepository;
    final private AuthorityRepository authorityRepository;

    // 처리 결과: CHANGED면 role은 바뀐 뒤 역할, INVALID_STATUS면 role은 지금 역할
    public record RoleChangeResult(Outcome outcome, String role, String modDate) {

        public enum Outcome {CHANGED, NOT_FOUND, INVALID_STATUS}

        public static RoleChangeResult changed(String role, String modDate) {
            return new RoleChangeResult(Outcome.CHANGED, role, modDate);
        }

        public static RoleChangeResult notFound() {
            return new RoleChangeResult(Outcome.NOT_FOUND, null, null);
        }

        public static RoleChangeResult invalidStatus(String currentRole) {
            return new RoleChangeResult(Outcome.INVALID_STATUS, currentRole, null);
        }

    }

    // 승인: PRE_USER → USER
    @Transactional
    public RoleChangeResult approve(int memberNo) {
        log.info("approve()");

        return changeRole(memberNo, ROLE_PRE_USER, ROLE_USER);

    }

    // 승인 취소: USER → PRE_USER
    @Transactional
    public RoleChangeResult unapprove(int memberNo) {
        log.info("unapprove()");

        return changeRole(memberNo, ROLE_USER, ROLE_PRE_USER);

    }

    private RoleChangeResult changeRole(int memberNo, String fromRole, String toRole) {

        Optional<MemberEntity> optionalMember = memberRepository.findById(memberNo);
        if (optionalMember.isEmpty()) {
            return RoleChangeResult.notFound();
        }

        MemberEntity member = optionalMember.get();
        String currentRole = member.getAuthorityEntity() != null
                ? member.getAuthorityEntity().getAuthRoleName()
                : null;
        if (!fromRole.equals(currentRole)) {
            return RoleChangeResult.invalidStatus(currentRole);
        }

        // 역할 행은 서버 시작 때 DataInitializer가 만든다. 없으면 설정 오류라 예외로 알린다.
        AuthorityEntity toAuthority = authorityRepository.findByAuthRoleName(toRole)
                .orElseThrow(() -> new IllegalStateException("역할이 없습니다: " + toRole));

        member.setAuthorityEntity(toAuthority);
        memberRepository.saveAndFlush(member);      // @PreUpdate가 수정 시각을 갱신한다

        String modDate = member.getMemModDate() != null ? member.getMemModDate().format(DATE_FORMAT) : null;
        return RoleChangeResult.changed(toRole, modDate);

    }

}
