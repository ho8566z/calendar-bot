package com.office.calendar.telegram;

import com.office.calendar.member.jpa.MemberEntity;
import com.office.calendar.member.jpa.MemberRepository;
import com.office.calendar.member.jpa.TelegramLinkEntity;
import com.office.calendar.member.jpa.TelegramLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 텔레그램 연동 코드 발급·확인·해제와 수신 설정
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramLinkServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 12, 0, 0);

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private TelegramLinkRepository telegramLinkRepository;
    @Mock
    private LinkCodeGenerator linkCodeGenerator;

    private TelegramProperties configured;
    private TelegramLinkService service;
    private MemberEntity alice;

    @BeforeEach
    void setUp() {
        configured = new TelegramProperties("token-value", "MyCalBot", 10);
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new TelegramLinkService(memberRepository, telegramLinkRepository, linkCodeGenerator, configured, clock);

        alice = MemberEntity.builder().memNo(7).memId("alice").build();
        when(memberRepository.findByMemId("alice")).thenReturn(Optional.of(alice));
        when(memberRepository.findById(7)).thenReturn(Optional.of(alice));
        when(linkCodeGenerator.generate()).thenReturn("CODE1234");
    }

    private TelegramLinkEntity link(String code, LocalDateTime expiresAt) {
        return TelegramLinkEntity.builder().code(code).memberNo(7).expiresAt(expiresAt).build();
    }

    // ---------------------------------------------------------------- 코드 발급

    @Test
    @DisplayName("발급: 10분 뒤 만료되는 코드를 저장하고 봇 링크를 돌려준다")
    void issue_ok() {
        TelegramLinkService.IssuedLink issued = service.issueCode("alice");

        assertThat(issued.outcome()).isEqualTo(TelegramLinkService.IssueOutcome.ISSUED);
        assertThat(issued.code()).isEqualTo("CODE1234");
        assertThat(issued.link()).isEqualTo("https://t.me/MyCalBot?start=CODE1234");
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusMinutes(10));

        ArgumentCaptor<TelegramLinkEntity> saved = ArgumentCaptor.forClass(TelegramLinkEntity.class);
        verify(telegramLinkRepository).save(saved.capture());
        assertThat(saved.getValue().getMemberNo()).isEqualTo(7);
        assertThat(saved.getValue().getCode()).isEqualTo("CODE1234");
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
    }

    @Test
    @DisplayName("발급: 이전 미사용 코드를 먼저 지우고(대체) 새 코드를 저장한다")
    void issue_replacesOldCode() {
        service.issueCode("alice");

        InOrder order = inOrder(telegramLinkRepository);
        order.verify(telegramLinkRepository).deleteByMemberNo(7);
        order.verify(telegramLinkRepository).save(any(TelegramLinkEntity.class));
    }

    @Test
    @DisplayName("발급: 이미 연동된 회원은 ALREADY_LINKED이고 코드를 만들지 않는다")
    void issue_alreadyLinked() {
        alice.setTelegramChatId(555L);

        TelegramLinkService.IssuedLink issued = service.issueCode("alice");

        assertThat(issued.outcome()).isEqualTo(TelegramLinkService.IssueOutcome.ALREADY_LINKED);
        assertThat(issued.code()).isNull();
        verify(telegramLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("발급: 봇이 설정되지 않았으면 NOT_CONFIGURED이고 DB를 건드리지 않는다")
    void issue_notConfigured() {
        TelegramProperties none = new TelegramProperties("", "", 10);
        TelegramLinkService s = new TelegramLinkService(memberRepository, telegramLinkRepository, linkCodeGenerator, none,
                Clock.fixed(Instant.now(), ZONE));

        assertThat(s.issueCode("alice").outcome()).isEqualTo(TelegramLinkService.IssueOutcome.NOT_CONFIGURED);
        verify(telegramLinkRepository, never()).save(any());
        verify(telegramLinkRepository, never()).deleteByMemberNo(Mockito.anyInt());
    }

    // ---------------------------------------------------------------- 연동 확인

    @Test
    @DisplayName("확인: 유효한 코드면 chat ID를 저장하고 코드를 지운다")
    void complete_ok() {
        TelegramLinkEntity l = link("CODE1234", NOW.plusMinutes(5));
        when(telegramLinkRepository.findById("CODE1234")).thenReturn(Optional.of(l));

        assertThat(service.completeLink("CODE1234", 555L)).isEqualTo(TelegramLinkService.LinkOutcome.LINKED);

        assertThat(alice.getTelegramChatId()).isEqualTo(555L);
        verify(memberRepository).saveAndFlush(alice);
        verify(telegramLinkRepository).delete(l);
    }

    @Test
    @DisplayName("확인: 없는 코드는 INVALID_CODE이고 아무것도 저장하지 않는다")
    void complete_unknown() {
        when(telegramLinkRepository.findById("NOPE")).thenReturn(Optional.empty());

        assertThat(service.completeLink("NOPE", 555L)).isEqualTo(TelegramLinkService.LinkOutcome.INVALID_CODE);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("확인: 만료된 코드는 거절하고 코드를 지운다(만료 시각 정각도 만료)")
    void complete_expired() {
        TelegramLinkEntity l = link("OLD", NOW);
        when(telegramLinkRepository.findById("OLD")).thenReturn(Optional.of(l));

        assertThat(service.completeLink("OLD", 555L)).isEqualTo(TelegramLinkService.LinkOutcome.INVALID_CODE);

        assertThat(alice.getTelegramChatId()).isNull();
        verify(telegramLinkRepository).delete(l);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("확인: 같은 코드를 다시 쓰면(1회용) 거절된다")
    void complete_reuse() {
        TelegramLinkEntity l = link("CODE1234", NOW.plusMinutes(5));
        when(telegramLinkRepository.findById("CODE1234")).thenReturn(Optional.of(l)).thenReturn(Optional.empty());

        assertThat(service.completeLink("CODE1234", 555L)).isEqualTo(TelegramLinkService.LinkOutcome.LINKED);
        assertThat(service.completeLink("CODE1234", 999L)).isEqualTo(TelegramLinkService.LinkOutcome.INVALID_CODE);
        assertThat(alice.getTelegramChatId()).isEqualTo(555L);
    }

    @Test
    @DisplayName("확인: 그 chat ID가 이미 다른 회원에 연결돼 있으면 거절하고 코드를 지운다")
    void complete_chatAlreadyLinked() {
        TelegramLinkEntity l = link("CODE1234", NOW.plusMinutes(5));
        when(telegramLinkRepository.findById("CODE1234")).thenReturn(Optional.of(l));
        when(memberRepository.existsByTelegramChatId(555L)).thenReturn(true);

        assertThat(service.completeLink("CODE1234", 555L)).isEqualTo(TelegramLinkService.LinkOutcome.CHAT_ALREADY_LINKED);

        assertThat(alice.getTelegramChatId()).isNull();
        verify(telegramLinkRepository).delete(l);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("확인: 이미 연동된 회원의 남은 코드는 MEMBER_ALREADY_LINKED이고 기존 chat ID를 바꾸지 않는다")
    void complete_memberAlreadyLinked() {
        alice.setTelegramChatId(111L);
        TelegramLinkEntity l = link("CODE1234", NOW.plusMinutes(5));
        when(telegramLinkRepository.findById("CODE1234")).thenReturn(Optional.of(l));

        assertThat(service.completeLink("CODE1234", 555L)).isEqualTo(TelegramLinkService.LinkOutcome.MEMBER_ALREADY_LINKED);

        assertThat(alice.getTelegramChatId()).isEqualTo(111L);
        verify(telegramLinkRepository).delete(l);
    }

    @Test
    @DisplayName("확인: 코드의 회원이 사라졌으면 INVALID_CODE")
    void complete_memberGone() {
        TelegramLinkEntity l = link("CODE1234", NOW.plusMinutes(5));
        when(telegramLinkRepository.findById("CODE1234")).thenReturn(Optional.of(l));
        when(memberRepository.findById(7)).thenReturn(Optional.empty());

        assertThat(service.completeLink("CODE1234", 555L)).isEqualTo(TelegramLinkService.LinkOutcome.INVALID_CODE);
        verify(telegramLinkRepository).delete(l);
    }

    @Test
    @DisplayName("확인: 형식이 틀린 코드(공백·기호·65자·null)는 DB를 조회하지 않고 거절한다")
    void complete_badFormat() {
        for (String bad : new String[]{null, "", " ", "a b", "x'; DROP", "한글", "a".repeat(65), "a/b", "a.b"}) {
            assertThat(service.completeLink(bad, 555L)).as("code=" + bad).isEqualTo(TelegramLinkService.LinkOutcome.INVALID_CODE);
        }
        verify(telegramLinkRepository, never()).findById(anyString());
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("확인: 딥링크가 허용하는 문자(영문·숫자·_·-)와 64자 코드는 조회한다")
    void complete_allowedFormat() {
        when(telegramLinkRepository.findById(anyString())).thenReturn(Optional.empty());

        for (String ok : new String[]{"abcABC123", "a_b-c", "a".repeat(64)}) {
            assertThat(service.completeLink(ok, 1L)).isEqualTo(TelegramLinkService.LinkOutcome.INVALID_CODE);
            verify(telegramLinkRepository).findById(ok);
        }
    }

    // ---------------------------------------------------------------- 해제·수신 설정·상태

    @Test
    @DisplayName("해제: chat ID를 비우고 수신도 끄며 남은 코드도 지운다")
    void unlink_clearsEverything() {
        alice.setTelegramChatId(555L);
        alice.setBriefYn(true);

        TelegramLinkService.Status status = service.unlink("alice");

        assertThat(status).isEqualTo(new TelegramLinkService.Status(false, false));
        assertThat(alice.getTelegramChatId()).isNull();
        assertThat(alice.isBriefYn()).isFalse();
        verify(memberRepository).save(alice);
        verify(telegramLinkRepository).deleteByMemberNo(7);
    }

    @Test
    @DisplayName("해제: 연동하지 않은 회원이 호출해도 오류 없이 같은 결과")
    void unlink_whenNotLinked() {
        assertThat(service.unlink("alice")).isEqualTo(new TelegramLinkService.Status(false, false));
    }

    @Test
    @DisplayName("수신 켜기: 연동하지 않았으면 NOT_LINKED이고 저장하지 않는다")
    void brief_enableWithoutLink() {
        assertThat(service.setBriefEnabled("alice", true)).isEqualTo(TelegramLinkService.BriefOutcome.NOT_LINKED);

        assertThat(alice.isBriefYn()).isFalse();
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("수신 켜기: 연동했으면 켜진다")
    void brief_enableWhenLinked() {
        alice.setTelegramChatId(555L);

        assertThat(service.setBriefEnabled("alice", true)).isEqualTo(TelegramLinkService.BriefOutcome.CHANGED);

        assertThat(alice.isBriefYn()).isTrue();
        verify(memberRepository).save(alice);
    }

    @Test
    @DisplayName("수신 끄기: 연동하지 않았어도 언제나 가능")
    void brief_disableAlwaysAllowed() {
        alice.setBriefYn(true);

        assertThat(service.setBriefEnabled("alice", false)).isEqualTo(TelegramLinkService.BriefOutcome.CHANGED);

        assertThat(alice.isBriefYn()).isFalse();
    }

    @Test
    @DisplayName("상태: 연동 여부와 수신 여부를 돌려준다")
    void status() {
        assertThat(service.getStatus("alice")).isEqualTo(new TelegramLinkService.Status(false, false));

        alice.setTelegramChatId(555L);
        alice.setBriefYn(true);
        assertThat(service.getStatus("alice")).isEqualTo(new TelegramLinkService.Status(true, true));
    }

    @Test
    @DisplayName("만료된 코드 정리는 지금 시각을 기준으로 한다")
    void purge() {
        when(telegramLinkRepository.deleteExpired(NOW)).thenReturn(3);

        assertThat(service.purgeExpired()).isEqualTo(3);
    }

    @Test
    @DisplayName("회원을 찾지 못하면 IllegalStateException")
    void unknownMember() {
        when(memberRepository.findByMemId("ghost")).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getStatus("ghost"))
                .isInstanceOf(IllegalStateException.class);
        verify(memberRepository, never()).save(any());
    }

}
