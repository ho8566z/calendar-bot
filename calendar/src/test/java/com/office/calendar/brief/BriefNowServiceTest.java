package com.office.calendar.brief;

import com.office.calendar.brief.mapper.BriefMapper;
import com.office.calendar.member.jpa.MemberEntity;
import com.office.calendar.member.jpa.MemberRepository;
import com.office.calendar.telegram.TelegramApiException;
import com.office.calendar.telegram.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// "지금 보내기": 본인에게 다음 주 브리핑을 미리 보냄
class BriefNowServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    // 시각을 직접 움직일 수 있는 시계(2026-11-08 일요일 21:00 KST에서 시작)
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    private MemberRepository memberRepository;
    private BriefMapper briefMapper;
    private BriefSender briefSender;
    private MutableClock clock;
    private BriefNowService service;
    private MemberEntity alice;

    @BeforeEach
    void setUp() {
        memberRepository = Mockito.mock(MemberRepository.class);
        briefMapper = Mockito.mock(BriefMapper.class);
        briefSender = Mockito.mock(BriefSender.class);
        clock = new MutableClock();
        service = new BriefNowService(memberRepository, briefMapper, briefSender, new TelegramProperties("tok", "MyCalBot", 10), clock);

        alice = MemberEntity.builder().memNo(1).memId("alice").telegramChatId(111L).build();
        when(memberRepository.findByMemId("alice")).thenReturn(Optional.of(alice));
        when(briefMapper.selectPlans("alice", 20261109, 20261115)).thenReturn(List.of(new BriefPlanRow(2026, 11, 10, "회의", "14:00")));
    }

    @Test
    @DisplayName("연동한 회원에게 다음 주 브리핑을 보내고 대상 주를 알려 준다")
    void sent() {
        BriefNowService.Result r = service.sendNow("alice");

        assertThat(r.outcome()).isEqualTo(BriefNowService.Outcome.SENT);
        assertThat(r.weekStart()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(r.empty()).isFalse();

        ArgumentCaptor<List<String>> parts = ArgumentCaptor.forClass(List.class);
        verify(briefSender).send(Mockito.eq(111L), parts.capture());
        assertThat(parts.getValue().get(0)).contains("11/10(화)", "14:00 회의");
    }

    @Test
    @DisplayName("다음 주 일정이 없으면 없다고 알리는 문구를 보내고 empty로 표시한다")
    void emptyWeek() {
        when(briefMapper.selectPlans("alice", 20261109, 20261115)).thenReturn(List.of());

        BriefNowService.Result r = service.sendNow("alice");

        assertThat(r.outcome()).isEqualTo(BriefNowService.Outcome.SENT);
        assertThat(r.empty()).isTrue();
        verify(briefSender).send(111L, List.of("다음 주(11/9 ~ 11/15)에는 등록된 일정이 없습니다."));
    }

    @Test
    @DisplayName("연동하지 않았으면 NOT_LINKED이고 아무것도 보내거나 조회하지 않는다")
    void notLinked() {
        alice.setTelegramChatId(null);

        assertThat(service.sendNow("alice").outcome()).isEqualTo(BriefNowService.Outcome.NOT_LINKED);

        verify(briefSender, never()).send(anyLong(), anyList());
        verify(briefMapper, never()).selectPlans(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    @DisplayName("봇 토큰이 없으면 NOT_CONFIGURED이고 회원 조회도 하지 않는다")
    void notConfigured() {
        BriefNowService s = new BriefNowService(memberRepository, briefMapper, briefSender, new TelegramProperties("", "", 10), clock);

        assertThat(s.sendNow("alice").outcome()).isEqualTo(BriefNowService.Outcome.NOT_CONFIGURED);

        verify(memberRepository, never()).findByMemId(Mockito.anyString());
        verify(briefSender, never()).send(anyLong(), anyList());
    }

    @Test
    @DisplayName("연속으로 누르면 10초 안에는 TOO_FREQUENT이고 남은 초를 알려 주며, 10초가 지나면 다시 보낼 수 있다")
    void cooldown() {
        assertThat(service.sendNow("alice").outcome()).isEqualTo(BriefNowService.Outcome.SENT);

        clock.advance(Duration.ofSeconds(3));
        BriefNowService.Result tooSoon = service.sendNow("alice");
        assertThat(tooSoon.outcome()).isEqualTo(BriefNowService.Outcome.TOO_FREQUENT);
        assertThat(tooSoon.retryAfterSeconds()).isBetween(7, 8);
        verify(briefSender, times(1)).send(anyLong(), anyList());

        clock.advance(Duration.ofSeconds(7));
        assertThat(service.sendNow("alice").outcome()).isEqualTo(BriefNowService.Outcome.SENT);
        verify(briefSender, times(2)).send(anyLong(), anyList());
    }

    @Test
    @DisplayName("간격 제한은 회원마다 따로 적용된다")
    void cooldownPerMember() {
        MemberEntity bob = MemberEntity.builder().memNo(2).memId("bob").telegramChatId(222L).build();
        when(memberRepository.findByMemId("bob")).thenReturn(Optional.of(bob));
        when(briefMapper.selectPlans("bob", 20261109, 20261115)).thenReturn(List.of());

        service.sendNow("alice");

        assertThat(service.sendNow("bob").outcome()).isEqualTo(BriefNowService.Outcome.SENT);
    }

    @Test
    @DisplayName("발송에 실패해도 간격은 지켜진다(연타로 텔레그램에 계속 요청하지 않음)")
    void failureStillCountsForCooldown() {
        doThrow(new TelegramApiException(500, 0)).when(briefSender).send(anyLong(), anyList());

        assertThat(service.sendNow("alice").outcome()).isEqualTo(BriefNowService.Outcome.SEND_FAILED);
        assertThat(service.sendNow("alice").outcome()).isEqualTo(BriefNowService.Outcome.TOO_FREQUENT);
        verify(briefSender, times(1)).send(anyLong(), anyList());
    }

    @Test
    @DisplayName("봇 차단(403)이면 SEND_FAILED와 함께 blocked로 알린다")
    void blocked() {
        doThrow(new TelegramApiException(403, 0)).when(briefSender).send(anyLong(), anyList());

        BriefNowService.Result r = service.sendNow("alice");

        assertThat(r.outcome()).isEqualTo(BriefNowService.Outcome.SEND_FAILED);
        assertThat(r.blocked()).isTrue();
    }

    @Test
    @DisplayName("그 밖의 발송 오류는 blocked가 아닌 SEND_FAILED")
    void otherFailure() {
        doThrow(new TelegramApiException(TelegramApiException.NETWORK_ERROR, 0)).when(briefSender).send(anyLong(), anyList());

        BriefNowService.Result r = service.sendNow("alice");

        assertThat(r.outcome()).isEqualTo(BriefNowService.Outcome.SEND_FAILED);
        assertThat(r.blocked()).isFalse();
    }

    @Test
    @DisplayName("회원을 찾지 못하면 IllegalStateException")
    void unknownMember() {
        when(memberRepository.findByMemId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendNow("ghost")).isInstanceOf(IllegalStateException.class);
    }

}
