package com.office.calendar.member.security;

import com.office.calendar.member.jpa.AuthorityEntity;
import com.office.calendar.member.jpa.MemberEntity;
import com.office.calendar.member.jpa.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// DB의 역할 이름이 보안 규칙이 쓰는 ROLE_ 권한으로 바뀌는지(경로 규칙의 전제)
@ExtendWith(MockitoExtension.class)
class MemberDetailsServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberDetailsService memberDetailsService;

    private MemberEntity memberWithRole(String id, String roleName) {
        return MemberEntity.builder()
                .memId(id).memPw("ENCODED")
                .authorityEntity(AuthorityEntity.builder().authNo((byte) 9).authRoleName(roleName).build())
                .build();
    }

    private java.util.List<String> authoritiesOf(UserDetails userDetails) {
        return userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("ADMIN 역할 회원은 ROLE_ADMIN 권한 하나를 갖는다")
    void admin() {
        when(memberRepository.findByMemId("boss")).thenReturn(Optional.of(memberWithRole("boss", "ADMIN")));

        UserDetails details = memberDetailsService.loadUserByUsername("boss");

        assertThat(details.getUsername()).isEqualTo("boss");
        assertThat(details.getPassword()).isEqualTo("ENCODED");
        assertThat(authoritiesOf(details)).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("USER와 PRE_USER도 같은 방식으로 ROLE_ 권한이 된다")
    void userAndPreUser() {
        when(memberRepository.findByMemId("alice")).thenReturn(Optional.of(memberWithRole("alice", "USER")));
        when(memberRepository.findByMemId("bob")).thenReturn(Optional.of(memberWithRole("bob", "PRE_USER")));

        assertThat(authoritiesOf(memberDetailsService.loadUserByUsername("alice"))).containsExactly("ROLE_USER");
        assertThat(authoritiesOf(memberDetailsService.loadUserByUsername("bob"))).containsExactly("ROLE_PRE_USER");
    }

    @Test
    @DisplayName("없는 아이디는 UsernameNotFoundException이다")
    void unknownUser() {
        when(memberRepository.findByMemId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

}
