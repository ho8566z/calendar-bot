package com.office.calendar.admin;

import com.office.calendar.member.jpa.AuthorityEntity;
import com.office.calendar.member.jpa.AuthorityRepository;
import com.office.calendar.member.jpa.MemberEntity;
import com.office.calendar.member.jpa.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 관리자 계정 시드(adm-005): 없을 때만 1개, 환경변수가 부족하거나 아이디가 겹치면 만들지 않는다
@ExtendWith(MockitoExtension.class)
class AdminSeedServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private AuthorityRepository authorityRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private final AuthorityEntity adminRole =
            AuthorityEntity.builder().authNo((byte) 3).authRoleName("ADMIN").build();

    private AdminSeedService service(String id, String password, String mail) {
        return new AdminSeedService(memberRepository, authorityRepository, passwordEncoder, id, password, mail);
    }

    @Test
    @DisplayName("관리자가 없고 환경변수가 갖춰지면 ADMIN 권한 계정을 1개 만든다")
    void seed_createsAdmin() {
        when(authorityRepository.findByAuthRoleName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("secret-value")).thenReturn("ENCODED");

        boolean created = service("boss", "secret-value", "boss@example.com").seedIfAbsent();

        assertThat(created).isTrue();
        ArgumentCaptor<MemberEntity> captor = ArgumentCaptor.forClass(MemberEntity.class);
        verify(memberRepository).save(captor.capture());
        MemberEntity saved = captor.getValue();
        assertThat(saved.getMemId()).isEqualTo("boss");
        assertThat(saved.getMemPw()).isEqualTo("ENCODED");
        assertThat(saved.getMemMail()).isEqualTo("boss@example.com");
        assertThat(saved.getAuthorityEntity().getAuthRoleName()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("메일이 비어 있으면 자리표시 값('-')으로 채운다(MAIL은 NOT NULL)")
    void seed_blankMailUsesPlaceholder() {
        when(authorityRepository.findByAuthRoleName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("secret-value")).thenReturn("ENCODED");

        service("boss", "secret-value", "").seedIfAbsent();

        ArgumentCaptor<MemberEntity> captor = ArgumentCaptor.forClass(MemberEntity.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getMemMail()).isEqualTo("-");
        assertThat(captor.getValue().getMemPhone()).isEqualTo("-");
    }

    @Test
    @DisplayName("관리자가 이미 있으면 아무것도 하지 않는다(재시작해도 1명)")
    void seed_skipsWhenAdminExists() {
        when(memberRepository.existsByAuthorityEntity_AuthRoleName("ADMIN")).thenReturn(true);

        boolean created = service("boss", "secret-value", "boss@example.com").seedIfAbsent();

        assertThat(created).isFalse();
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("ADMIN_ID가 비어 있으면 만들지 않는다")
    void seed_skipsWhenIdBlank() {
        boolean created = service("", "secret-value", "").seedIfAbsent();

        assertThat(created).isFalse();
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("ADMIN_PASSWORD가 비어 있으면 만들지 않는다")
    void seed_skipsWhenPasswordBlank() {
        boolean created = service("boss", "  ", "").seedIfAbsent();

        assertThat(created).isFalse();
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("아이디가 컬럼 길이(20자)를 넘으면 만들지 않는다")
    void seed_skipsWhenIdTooLong() {
        boolean created = service("a".repeat(21), "secret-value", "").seedIfAbsent();

        assertThat(created).isFalse();
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 아이디의 일반 회원이 있으면 그 회원을 관리자로 바꾸지 않는다")
    void seed_skipsWhenIdAlreadyUsed() {
        when(memberRepository.existsByMemId("boss")).thenReturn(true);

        boolean created = service("boss", "secret-value", "").seedIfAbsent();

        assertThat(created).isFalse();
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("ADMIN 역할이 없으면 만들지 않는다")
    void seed_skipsWhenRoleMissing() {
        when(authorityRepository.findByAuthRoleName("ADMIN")).thenReturn(Optional.empty());

        boolean created = service("boss", "secret-value", "").seedIfAbsent();

        assertThat(created).isFalse();
        verify(memberRepository, never()).save(any());
    }

}
