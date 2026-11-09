package com.office.calendar.telegram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 봇 설정(brf-007): 토큰은 어디에도 출력되지 않고, 설정이 비면 기능만 꺼진다
class TelegramPropertiesTest {

    @Test
    @DisplayName("토큰과 올바른 봇 이름이 모두 있어야 설정된 것으로 본다")
    void configured() {
        assertThat(new TelegramProperties("t", "MyCalBot", 10).isConfigured()).isTrue();
        assertThat(new TelegramProperties("", "MyCalBot", 10).isConfigured()).isFalse();
        assertThat(new TelegramProperties("t", "", 10).isConfigured()).isFalse();
        assertThat(new TelegramProperties(null, null, 10).isConfigured()).isFalse();
        assertThat(new TelegramProperties("  ", "MyCalBot", 10).isConfigured()).isFalse();
    }

    @Test
    @DisplayName("토큰에 주소에 안전하지 않은 문자(공백·슬래시·중괄호·물음표 등)가 있으면 설정되지 않은 것으로 본다")
    void tokenFormat() {
        assertThat(new TelegramProperties("123456:ABC-def_GHI", "MyCalBot", 10).isConfigured()).isTrue();
        assertThat(new TelegramProperties("123456:ABC-def_GHI", "MyCalBot", 10).hasValidToken()).isTrue();

        for (String bad : new String[]{"12 34:abc", "123/456:abc", "123:{token}", "123:abc?x=1", "123:abc#", "123:한글"}) {
            assertThat(new TelegramProperties(bad, "MyCalBot", 10).hasValidToken()).as(bad).isFalse();
            assertThat(new TelegramProperties(bad, "MyCalBot", 10).isConfigured()).as(bad).isFalse();
        }
    }

    @Test
    @DisplayName("봇 이름은 앞의 @와 공백을 지우고, 형식이 틀리면(짧음·기호·공백 포함) 설정되지 않은 것으로 본다")
    void username() {
        assertThat(new TelegramProperties("t", " @MyCalBot ", 10).botUsername()).isEqualTo("MyCalBot");
        assertThat(new TelegramProperties("t", "@MyCalBot", 10).isConfigured()).isTrue();

        for (String bad : new String[]{"abc", "my bot", "my-bot-1", "a".repeat(33), "봇이름입니다만"}) {
            assertThat(new TelegramProperties("t", bad, 10).isConfigured()).as(bad).isFalse();
        }
    }

    @Test
    @DisplayName("연동 링크는 t.me/<봇이름>?start=<코드> 형식")
    void linkUrl() {
        assertThat(new TelegramProperties("t", "MyCalBot", 10).linkUrl("ABC123")).isEqualTo("https://t.me/MyCalBot?start=ABC123");
    }

    @Test
    @DisplayName("유효 분이 0 이하이면 기본 10분")
    void minutes() {
        assertThat(new TelegramProperties("t", "MyCalBot", 0).linkMinutes()).isEqualTo(10);
        assertThat(new TelegramProperties("t", "MyCalBot", -5).linkMinutes()).isEqualTo(10);
        assertThat(new TelegramProperties("t", "MyCalBot", 3).linkMinutes()).isEqualTo(3);
    }

    @Test
    @DisplayName("toString에 토큰이 나오지 않는다")
    void toStringHidesToken() {
        TelegramProperties p = new TelegramProperties("SECRET-TOKEN-123", "MyCalBot", 10);

        assertThat(p.toString()).doesNotContain("SECRET-TOKEN-123");
        assertThat(p.toString()).doesNotContain("MyCalBot");
    }

    @Test
    @DisplayName("연동 코드는 영문·숫자 32자이고 매번 다르다")
    void codeGenerator() {
        LinkCodeGenerator generator = new LinkCodeGenerator();
        java.util.Set<String> seen = new java.util.HashSet<>();

        for (int i = 0; i < 200; i++) {
            String code = generator.generate();
            assertThat(code).hasSize(32).matches("[A-Za-z0-9]+");
            seen.add(code);
        }
        assertThat(seen).hasSize(200);
    }

}
