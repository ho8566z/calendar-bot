package com.office.calendar.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

// 텔레그램 봇 설정(brf-007). 토큰은 소스에 두지 않고 환경변수 TELEGRAM_BOT_TOKEN으로만 받는다.
// 토큰이 요청 주소 경로에 들어가므로 이 클래스는 토큰을 출력(toString)하거나 로그에 남기지 않는다.
@Component
public class TelegramProperties {

    // 봇 사용자명(@BotFather가 정한 이름): 영문·숫자·밑줄 5~32자
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{5,32}$");

    // 봇 토큰은 요청 주소 경로에 그대로 들어가므로 주소에 안전한 문자(영문·숫자·콜론·밑줄·하이픈)만 허용한다.
    // 이 밖의 문자(공백, 슬래시, 중괄호 등)가 있으면 잘못 설정된 것으로 보고 기능을 켜지 않는다.
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9:_-]+$");

    private final String botToken;
    private final String botUsername;
    private final int linkMinutes;

    public TelegramProperties(
            @Value("${telegram.bot.token:}") String botToken,
            @Value("${telegram.bot.username:}") String botUsername,
            @Value("${telegram.link-minutes:10}") int linkMinutes) {
        this.botToken = botToken == null ? "" : botToken.trim();
        this.botUsername = normalizeUsername(botUsername);
        this.linkMinutes = linkMinutes > 0 ? linkMinutes : 10;
    }

    // 앞의 @와 공백을 지운다
    static String normalizeUsername(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.trim();
        return name.startsWith("@") ? name.substring(1) : name;
    }

    // 토큰과 사용자명이 모두 설정돼 연동·발송을 쓸 수 있는지
    public boolean isConfigured() {
        return hasValidToken() && USERNAME.matcher(botUsername).matches();
    }

    // 토큰이 주소에 넣어도 안전한 형식인지(발송·폴링은 봇 이름 없이 토큰만 있으면 된다)
    public boolean hasValidToken() {
        return TOKEN.matcher(botToken).matches();
    }

    // 토큰 값. 주소를 만드는 텔레그램 클라이언트만 쓴다
    String botToken() {
        return botToken;
    }

    public String botUsername() {
        return botUsername;
    }

    public int linkMinutes() {
        return linkMinutes;
    }

    // 사용자에게 보여 줄 연동 주소(딥링크)
    public String linkUrl(String code) {
        return "https://t.me/" + botUsername + "?start=" + code;
    }

    @Override
    public String toString() {
        return "TelegramProperties{configured=" + isConfigured() + ", linkMinutes=" + linkMinutes + "}";
    }

}
