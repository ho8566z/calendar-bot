package com.office.calendar.member.jpa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 새 회원 저장 직전(@PrePersist)의 권한 처리
class MemberEntityTest {

    @Test
    @DisplayName("권한이 없는 새 회원(회원가입)은 PRE_USER로 시작한다")
    void onCreate_defaultsToPreUser() {
        MemberEntity member = MemberEntity.builder().memId("alice").build();

        member.onCreate();

        assertThat(member.getAuthorityEntity().getAuthRoleName()).isEqualTo("PRE_USER");
        assertThat(member.getMemRegDate()).isNotNull();
    }

    @Test
    @DisplayName("미리 정한 권한(관리자 시드)은 PRE_USER로 덮어쓰지 않는다")
    void onCreate_keepsPresetAuthority() {
        AuthorityEntity admin = AuthorityEntity.builder().authNo((byte) 3).authRoleName("ADMIN").build();
        MemberEntity member = MemberEntity.builder().memId("boss").authorityEntity(admin).build();

        member.onCreate();

        assertThat(member.getAuthorityEntity().getAuthRoleName()).isEqualTo("ADMIN");
    }

}
