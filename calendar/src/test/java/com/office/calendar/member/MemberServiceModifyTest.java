package com.office.calendar.member;

import com.office.calendar.member.jpa.MemberEntity;
import com.office.calendar.member.jpa.MemberRepository;
import com.office.calendar.member.mapper.MemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 내 정보 수정 대상 고정(auth-004): 폼의 회원번호(no)를 바꿔 보내도 본인만 수정돼야 한다
@ExtendWith(MockitoExtension.class)
class MemberServiceModifyTest {

    @Mock
    private MemberDao memberDao;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JavaMailSender javaMailSender;
    @Mock
    private MemberMapper memberMapper;
    @Mock
    private MemberRepository memberRepository;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberDao, passwordEncoder, javaMailSender, memberMapper, memberRepository);
    }

    @Test
    @DisplayName("폼의 회원번호가 달라도 로그인한 본인의 정보만 수정된다")
    void modifyConfirm_onlyLoggedInMember() {
        MemberEntity alice = MemberEntity.builder().memNo(1).memId("alice").memPw("old").memMail("a@x").memPhone("010").build();
        when(memberRepository.findByMemId("alice")).thenReturn(Optional.of(alice));
        when(passwordEncoder.encode("newpw")).thenReturn("ENCODED");
        // 공격자가 다른 회원의 번호(999)를 넣어 보낸 폼
        MemberDto form = MemberDto.builder().no(999).pw("newpw").mail("new@x").phone("011").build();

        int result = memberService.modifyConfirm(form, "alice");

        assertThat(result).isEqualTo(MemberService.MODIFY_SUCCESS);
        assertThat(alice.getMemPw()).isEqualTo("ENCODED");
        assertThat(alice.getMemMail()).isEqualTo("new@x");
        assertThat(alice.getMemPhone()).isEqualTo("011");
        verify(memberRepository, never()).findById(any());
    }

    @Test
    @DisplayName("로그인한 회원이 없으면 수정에 실패한다")
    void modifyConfirm_noSuchMember() {
        when(memberRepository.findByMemId("ghost")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("newpw")).thenReturn("ENCODED");
        MemberDto form = MemberDto.builder().no(1).pw("newpw").mail("m").phone("p").build();

        int result = memberService.modifyConfirm(form, "ghost");

        assertThat(result).isEqualTo(MemberService.MODIFY_FAIL);
    }

}
