package com.office.calendar.brief;

/** 브리핑을 받을 회원: 수신을 켰고 텔레그램이 연동된 USER. */
public record BriefTarget(int memberNo, String memberId, Long chatId) {
}
