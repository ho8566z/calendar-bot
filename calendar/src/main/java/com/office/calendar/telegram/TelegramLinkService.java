package com.office.calendar.telegram;

import com.office.calendar.member.jpa.MemberEntity;
import com.office.calendar.member.jpa.MemberRepository;
import com.office.calendar.member.jpa.TelegramLinkEntity;
import com.office.calendar.member.jpa.TelegramLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

// 텔레그램 연동(brf-005, brf-006)과 브리핑 수신 설정(brf-003).
// 연동 코드와 봇 토큰은 로그에 남기지 않는다.
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramLinkService {

    // 딥링크 start 값으로 허용되는 문자(영문·숫자·_·-, 최대 64자). 이 밖의 값은 DB를 조회하지 않고 거절한다
    private static final Pattern CODE_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    public enum IssueOutcome { ISSUED, ALREADY_LINKED, NOT_CONFIGURED }

    public enum LinkOutcome { LINKED, INVALID_CODE, CHAT_ALREADY_LINKED, MEMBER_ALREADY_LINKED }

    public enum BriefOutcome { CHANGED, NOT_LINKED }

    // 코드 발급 결과(ISSUED일 때만 code, link, expiresAt이 있다)
    public record IssuedLink(IssueOutcome outcome, String code, String link, LocalDateTime expiresAt) {
    }

    // 연동 상태
    public record Status(boolean linked, boolean briefEnabled) {
    }

    final private MemberRepository memberRepository;
    final private TelegramLinkRepository telegramLinkRepository;
    final private LinkCodeGenerator linkCodeGenerator;
    final private TelegramProperties properties;
    final private Clock clock;

    // 연동 코드 발급: 이미 연동돼 있으면 거절하고, 이전 미사용 코드는 새 코드로 대체한다
    @Transactional
    public IssuedLink issueCode(String memberId) {
        log.info("issueCode()");

        if (!properties.isConfigured()) {
            return new IssuedLink(IssueOutcome.NOT_CONFIGURED, null, null, null);
        }

        MemberEntity member = findMember(memberId);
        if (member.getTelegramChatId() != null) {
            return new IssuedLink(IssueOutcome.ALREADY_LINKED, null, null, null);
        }

        telegramLinkRepository.deleteByMemberNo(member.getMemNo());

        LocalDateTime now = LocalDateTime.now(clock);
        TelegramLinkEntity link = TelegramLinkEntity.builder()
                .code(linkCodeGenerator.generate())
                .memberNo(member.getMemNo())
                .expiresAt(now.plusMinutes(properties.linkMinutes()))
                .build();
        telegramLinkRepository.save(link);

        return new IssuedLink(IssueOutcome.ISSUED, link.getCode(), properties.linkUrl(link.getCode()), link.getExpiresAt());

    }

    @Transactional(readOnly = true)
    public Status getStatus(String memberId) {
        log.info("getStatus()");

        MemberEntity member = findMember(memberId);
        return new Status(member.getTelegramChatId() != null, member.isBriefYn());

    }

    // 연동 해제: chat ID를 비우고 브리핑 수신도 끈다. 미사용 코드도 지운다. 미연동 상태에서도 오류가 아니다
    @Transactional
    public Status unlink(String memberId) {
        log.info("unlink()");

        MemberEntity member = findMember(memberId);
        member.setTelegramChatId(null);
        member.setBriefYn(false);
        memberRepository.save(member);
        telegramLinkRepository.deleteByMemberNo(member.getMemNo());

        return new Status(false, false);

    }

    // 브리핑 수신 켜기/끄기: 텔레그램이 연동된 회원만 켤 수 있다(끄기는 언제나 가능)
    @Transactional
    public BriefOutcome setBriefEnabled(String memberId, boolean enabled) {
        log.info("setBriefEnabled()");

        MemberEntity member = findMember(memberId);
        if (enabled && member.getTelegramChatId() == null) {
            return BriefOutcome.NOT_LINKED;
        }

        member.setBriefYn(enabled);
        memberRepository.save(member);

        return BriefOutcome.CHANGED;

    }

    // 봇이 받은 '/start 코드'를 확인해 chat ID를 저장한다(폴러가 호출). 코드는 1회용이라 결과와 관계없이 지운다.
    @Transactional
    public LinkOutcome completeLink(String code, long chatId) {
        log.info("completeLink()");

        if (code == null || !CODE_FORMAT.matcher(code).matches()) {
            return LinkOutcome.INVALID_CODE;
        }

        TelegramLinkEntity link = telegramLinkRepository.findById(code).orElse(null);
        if (link == null) {
            return LinkOutcome.INVALID_CODE;
        }

        if (link.isExpired(LocalDateTime.now(clock))) {
            telegramLinkRepository.delete(link);
            return LinkOutcome.INVALID_CODE;
        }

        MemberEntity member = memberRepository.findById(link.getMemberNo()).orElse(null);
        if (member == null) {
            telegramLinkRepository.delete(link);
            return LinkOutcome.INVALID_CODE;
        }

        if (member.getTelegramChatId() != null) {
            telegramLinkRepository.delete(link);
            return LinkOutcome.MEMBER_ALREADY_LINKED;
        }

        if (memberRepository.existsByTelegramChatId(chatId)) {
            telegramLinkRepository.delete(link);
            return LinkOutcome.CHAT_ALREADY_LINKED;
        }

        member.setTelegramChatId(chatId);
        memberRepository.saveAndFlush(member);
        telegramLinkRepository.delete(link);

        return LinkOutcome.LINKED;

    }

    // 만료된 미사용 코드를 지운다. 지운 개수를 돌려준다
    @Transactional
    public int purgeExpired() {
        return telegramLinkRepository.deleteExpired(LocalDateTime.now(clock));

    }

    private MemberEntity findMember(String memberId) {
        return memberRepository.findByMemId(memberId)
                .orElseThrow(() -> new IllegalStateException("로그인한 회원을 찾을 수 없다"));

    }

}
