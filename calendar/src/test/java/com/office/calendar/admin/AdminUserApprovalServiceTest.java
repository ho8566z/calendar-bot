package com.office.calendar.admin;

import com.office.calendar.admin.AdminUserApprovalService.RoleChangeResult;
import com.office.calendar.admin.AdminUserApprovalService.RoleChangeResult.Outcome;
import com.office.calendar.member.jpa.AuthorityEntity;
import com.office.calendar.member.jpa.AuthorityRepository;
import com.office.calendar.member.jpa.MemberEntity;
import com.office.calendar.member.jpa.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 사용자 승인·승인 취소(adm-002): 상태 전이, 없는 회원, 맞지 않는 상태, ADMIN 거절
@ExtendWith(MockitoExtension.class)
class AdminUserApprovalServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private AuthorityRepository authorityRepository;

    @InjectMocks
    private AdminUserApprovalService service;

    private final AuthorityEntity preUser = AuthorityEntity.builder().authNo((byte) 1).authRoleName("PRE_USER").build();
    private final AuthorityEntity user = AuthorityEntity.builder().authNo((byte) 2).authRoleName("USER").build();
    private final AuthorityEntity admin = AuthorityEntity.builder().authNo((byte) 3).authRoleName("ADMIN").build();

    private MemberEntity memberWith(AuthorityEntity authority) {
        return MemberEntity.builder()
                .memNo(5).memId("alice")
                .memModDate(LocalDateTime.of(2026, 11, 8, 10, 30, 0))
                .authorityEntity(authority)
                .build();
    }

    @Test
    @DisplayName("승인: PRE_USER가 USER가 되고 저장되며 수정 시각이 돌아온다")
    void approve_preUserBecomesUser() {
        MemberEntity member = memberWith(preUser);
        when(memberRepository.findById(5)).thenReturn(Optional.of(member));
        when(authorityRepository.findByAuthRoleName("USER")).thenReturn(Optional.of(user));

        RoleChangeResult result = service.approve(5);

        assertThat(result.outcome()).isEqualTo(Outcome.CHANGED);
        assertThat(result.role()).isEqualTo("USER");
        assertThat(result.modDate()).isEqualTo("2026-11-08 10:30:00");
        assertThat(member.getAuthorityEntity()).isSameAs(user);
        verify(memberRepository).saveAndFlush(member);
    }

    @Test
    @DisplayName("승인 취소: USER가 PRE_USER가 된다")
    void unapprove_userBecomesPreUser() {
        MemberEntity member = memberWith(user);
        when(memberRepository.findById(5)).thenReturn(Optional.of(member));
        when(authorityRepository.findByAuthRoleName("PRE_USER")).thenReturn(Optional.of(preUser));

        RoleChangeResult result = service.unapprove(5);

        assertThat(result.outcome()).isEqualTo(Outcome.CHANGED);
        assertThat(result.role()).isEqualTo("PRE_USER");
        assertThat(member.getAuthorityEntity()).isSameAs(preUser);
        verify(memberRepository).saveAndFlush(member);
    }

    @Test
    @DisplayName("없는 회원이면 NOT_FOUND이고 아무것도 저장하지 않는다")
    void approve_notFound() {
        when(memberRepository.findById(404)).thenReturn(Optional.empty());

        RoleChangeResult result = service.approve(404);

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_FOUND);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미 USER인 회원을 승인하면 INVALID_STATUS이고 지금 역할을 알려 준다")
    void approve_alreadyUser() {
        MemberEntity member = memberWith(user);
        when(memberRepository.findById(5)).thenReturn(Optional.of(member));

        RoleChangeResult result = service.approve(5);

        assertThat(result.outcome()).isEqualTo(Outcome.INVALID_STATUS);
        assertThat(result.role()).isEqualTo("USER");
        assertThat(member.getAuthorityEntity()).isSameAs(user);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("PRE_USER에게 승인 취소를 하면 INVALID_STATUS이다")
    void unapprove_preUser() {
        MemberEntity member = memberWith(preUser);
        when(memberRepository.findById(5)).thenReturn(Optional.of(member));

        RoleChangeResult result = service.unapprove(5);

        assertThat(result.outcome()).isEqualTo(Outcome.INVALID_STATUS);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("ADMIN은 승인으로도 승인 취소로도 바뀌지 않는다(관리자 본인 포함)")
    void admin_isNeverChanged() {
        MemberEntity boss = memberWith(admin);
        when(memberRepository.findById(5)).thenReturn(Optional.of(boss));

        assertThat(service.approve(5).outcome()).isEqualTo(Outcome.INVALID_STATUS);
        assertThat(service.unapprove(5).outcome()).isEqualTo(Outcome.INVALID_STATUS);

        assertThat(boss.getAuthorityEntity()).isSameAs(admin);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("권한이 비어 있는 회원은 상태가 맞지 않는 것으로 본다")
    void noAuthority() {
        when(memberRepository.findById(5)).thenReturn(Optional.of(memberWith(null)));

        assertThat(service.approve(5).outcome()).isEqualTo(Outcome.INVALID_STATUS);
    }

    @Test
    @DisplayName("바꿀 역할이 DB에 없으면 설정 오류로 예외를 던지고 저장하지 않는다")
    void roleMissing() {
        when(memberRepository.findById(5)).thenReturn(Optional.of(memberWith(preUser)));
        when(authorityRepository.findByAuthRoleName("USER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(5)).isInstanceOf(IllegalStateException.class);
        verify(memberRepository, never()).saveAndFlush(any());
    }

}
