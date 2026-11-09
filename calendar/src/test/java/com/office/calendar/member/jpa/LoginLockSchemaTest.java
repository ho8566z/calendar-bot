package com.office.calendar.member.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// 로그인 시도 제한 스키마(sec-006): 테이블·컬럼 이름은 대문자 그대로(T-0021), 키는 (LOGIN_ID, CLIENT_IP).
// 실제 DDL은 DB가 있어야 확인되므로 실행 점검에서 다시 확인한다.
class LoginLockSchemaTest {

    private static Column column(String field) throws NoSuchFieldException {
        Field f = LoginLockEntity.class.getDeclaredField(field);
        return f.getAnnotation(Column.class);
    }

    @Test
    @DisplayName("LOGIN_LOCK: 아이디(20자)+IP(45자)가 복합 키이고 나머지는 키가 아니다")
    void keyColumns() throws Exception {
        assertThat(LoginLockEntity.class.getAnnotation(Table.class).name()).isEqualTo("LOGIN_LOCK");

        List<String> ids = Arrays.stream(LoginLockEntity.class.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(Id.class))
                .map(Field::getName)
                .collect(Collectors.toList());
        assertThat(ids).containsExactlyInAnyOrder("loginId", "clientIp");

        assertThat(column("loginId").name()).isEqualTo("LOGIN_ID");
        assertThat(column("loginId").length()).isEqualTo(20);
        assertThat(column("clientIp").name()).isEqualTo("CLIENT_IP");
        assertThat(column("clientIp").length()).isEqualTo(45);
    }

    @Test
    @DisplayName("LOGIN_LOCK: 횟수·수정 시각은 필수, 해제 시각은 NULL 허용, 시각은 DATETIME(6)")
    void otherColumns() throws Exception {
        assertThat(column("failCount").name()).isEqualTo("FAIL_COUNT");
        assertThat(column("failCount").nullable()).isFalse();
        assertThat(column("lockedUntil").name()).isEqualTo("LOCKED_UNTIL");
        assertThat(column("lockedUntil").columnDefinition()).isEqualTo("DATETIME(6) NULL");
        assertThat(column("updatedAt").name()).isEqualTo("UPDATED_AT");
        assertThat(column("updatedAt").nullable()).isFalse();
        assertThat(column("updatedAt").columnDefinition()).isEqualTo("DATETIME(6)");
    }

    @Test
    @DisplayName("해제 시각 전까지만 잠금이고, 해제 시각과 같은 순간부터 풀린다")
    void isLocked() {
        LocalDateTime until = LocalDateTime.of(2026, 11, 9, 12, 10, 0);
        LoginLockEntity lock = LoginLockEntity.builder().loginId("alice").clientIp("1.2.3.4").lockedUntil(until).build();

        assertThat(lock.isLocked(until.minusSeconds(1))).isTrue();
        assertThat(lock.isLocked(until)).isFalse();
        assertThat(lock.isLocked(until.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("해제 시각이 없으면(횟수만 쌓인 상태) 잠금이 아니다")
    void notLockedWithoutUntil() {
        LoginLockEntity lock = LoginLockEntity.builder().loginId("alice").clientIp("1.2.3.4").failCount(4).build();

        assertThat(lock.isLocked(LocalDateTime.of(2026, 11, 9, 12, 0, 0))).isFalse();
    }

}
