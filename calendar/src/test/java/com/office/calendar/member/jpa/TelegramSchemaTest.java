package com.office.calendar.member.jpa;

import com.office.calendar.brief.jpa.BriefLogEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// 브리핑용 스키마(brf-003~005): 테이블·컬럼 이름은 대문자 그대로 쓰고(T-0021), 유니크 제약으로 중복을 막는다.
// 실제 DDL은 DB가 있어야 확인되므로 실행 점검에서 다시 확인한다.
class TelegramSchemaTest {

    private static Column column(Class<?> type, String field) throws NoSuchFieldException {
        Field f = type.getDeclaredField(field);
        return f.getAnnotation(Column.class);
    }

    private static List<String> uniques(Class<?> type) {
        return Arrays.stream(type.getAnnotation(Table.class).uniqueConstraints())
                .map(UniqueConstraint::columnNames)
                .map(cols -> String.join("+", cols))
                .collect(Collectors.toList());
    }

    @Test
    @DisplayName("USER_MEMBER: TELEGRAM_CHAT_ID는 NULL 허용·유니크, BRIEF_YN은 NOT NULL이고 기본 끔")
    void memberColumns() throws Exception {
        assertThat(column(MemberEntity.class, "telegramChatId").name()).isEqualTo("TELEGRAM_CHAT_ID");
        assertThat(column(MemberEntity.class, "telegramChatId").nullable()).isTrue();
        assertThat(uniques(MemberEntity.class)).contains("ID", "TELEGRAM_CHAT_ID");

        Column brief = column(MemberEntity.class, "briefYn");
        assertThat(brief.name()).isEqualTo("BRIEF_YN");
        assertThat(brief.nullable()).isFalse();
        assertThat(brief.columnDefinition()).containsIgnoringCase("DEFAULT 0");
    }

    @Test
    @DisplayName("새 회원은 텔레그램 미연동(chat ID 없음)이고 브리핑 수신이 꺼져 있다")
    void memberDefaults() {
        MemberEntity member = MemberEntity.builder().memId("alice").build();

        assertThat(member.getTelegramChatId()).isNull();
        assertThat(member.isBriefYn()).isFalse();
    }

    @Test
    @DisplayName("TELEGRAM_LINK: 코드가 PK(64자), 회원당 1개(MEMBER_NO 유니크), 만료 시각 필수")
    void linkColumns() throws Exception {
        assertThat(TelegramLinkEntity.class.getAnnotation(Table.class).name()).isEqualTo("TELEGRAM_LINK");
        assertThat(column(TelegramLinkEntity.class, "code").name()).isEqualTo("CODE");
        assertThat(column(TelegramLinkEntity.class, "code").length()).isEqualTo(64);
        assertThat(uniques(TelegramLinkEntity.class)).containsExactly("MEMBER_NO");
        assertThat(column(TelegramLinkEntity.class, "expiresAt").name()).isEqualTo("EXPIRES_AT");
        assertThat(column(TelegramLinkEntity.class, "expiresAt").nullable()).isFalse();
        assertThat(column(TelegramLinkEntity.class, "memberNo").name()).isEqualTo("MEMBER_NO");
    }

    @Test
    @DisplayName("연동 코드는 만료 시각이 되는 순간부터 만료다")
    void linkExpiry() {
        LocalDateTime expires = LocalDateTime.of(2026, 11, 8, 12, 10, 0);
        TelegramLinkEntity link = TelegramLinkEntity.builder().code("abc").memberNo(1).expiresAt(expires).build();

        assertThat(link.isExpired(expires.minusSeconds(1))).isFalse();
        assertThat(link.isExpired(expires)).isTrue();
        assertThat(link.isExpired(expires.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("BRIEF_LOG: (MEMBER_NO, WEEK_START)가 유니크이고 결과는 문자열로 저장한다")
    void briefLogColumns() throws Exception {
        assertThat(BriefLogEntity.class.getAnnotation(Table.class).name()).isEqualTo("BRIEF_LOG");
        assertThat(uniques(BriefLogEntity.class)).containsExactly("MEMBER_NO+WEEK_START");
        assertThat(column(BriefLogEntity.class, "weekStart").name()).isEqualTo("WEEK_START");
        assertThat(column(BriefLogEntity.class, "result").name()).isEqualTo("RESULT");
        assertThat(column(BriefLogEntity.class, "result").length()).isEqualTo(10);
        assertThat(column(BriefLogEntity.class, "result").columnDefinition()).as("MySQL ENUM 이 아니라 VARCHAR(10)").isEqualTo("VARCHAR(10)");
        assertThat(column(BriefLogEntity.class, "errorMsg").name()).isEqualTo("ERROR_MSG");
        assertThat(column(BriefLogEntity.class, "errorMsg").length()).isEqualTo(BriefLogEntity.ERROR_MSG_MAX).isEqualTo(255);
        assertThat(column(BriefLogEntity.class, "sentAt").nullable()).isTrue();
    }

}
