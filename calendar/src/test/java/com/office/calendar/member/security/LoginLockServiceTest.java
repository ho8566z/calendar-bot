package com.office.calendar.member.security;

import com.office.calendar.member.jpa.LoginLockEntity;
import com.office.calendar.member.jpa.LoginLockId;
import com.office.calendar.member.jpa.LoginLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// 로그인 시도 제한 서비스. 리포지토리는 메모리 Map으로 DB의 동작(원자적 +1, 잠금, 삭제)을 흉내 낸다.
class LoginLockServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 9, 12, 0, 0);

    private final Map<LoginLockId, LoginLockEntity> table = new HashMap<>();
    private LoginLockRepository repository;
    private MutableClock clock;
    private LoginLockService service;

    // 시각을 옮길 수 있는 시계
    static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(LocalDateTime start) {
            this.instant = start.atZone(ZONE).toInstant();
        }

        void plusMinutes(long minutes) {
            instant = instant.plusSeconds(minutes * 60);
        }

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
            return instant;
        }
    }

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(LoginLockRepository.class);
        clock = new MutableClock(START);
        service = new LoginLockService(repository, clock, 5, 10);

        when(repository.findById(any(LoginLockId.class)))
                .thenAnswer(inv -> Optional.ofNullable(table.get(inv.<LoginLockId>getArgument(0))));
        when(repository.increaseFailCount(anyString(), anyString(), any(LocalDateTime.class))).thenAnswer(inv -> {
            LoginLockEntity row = table.get(new LoginLockId(inv.getArgument(0), inv.getArgument(1)));
            if (row == null) {
                return 0;
            }
            row.setFailCount(row.getFailCount() + 1);
            row.setUpdatedAt(inv.getArgument(2));
            return 1;
        });
        when(repository.saveAndFlush(any(LoginLockEntity.class))).thenAnswer(inv -> {
            LoginLockEntity row = inv.getArgument(0);
            table.put(new LoginLockId(row.getLoginId(), row.getClientIp()), row);
            return row;
        });
        when(repository.lock(anyString(), anyString(), anyInt(), any(LocalDateTime.class))).thenAnswer(inv -> {
            LoginLockEntity row = table.get(new LoginLockId(inv.getArgument(0), inv.getArgument(1)));
            if (row == null || row.getFailCount() < (int) inv.getArgument(2)) {
                return 0;
            }
            row.setLockedUntil(inv.getArgument(3));
            return 1;
        });
        when(repository.deleteByKey(anyString(), anyString()))
                .thenAnswer(inv -> table.remove(new LoginLockId(inv.getArgument(0), inv.getArgument(1))) == null ? 0 : 1);
        when(repository.deleteExpiredLock(anyString(), anyString(), any(LocalDateTime.class))).thenAnswer(inv -> {
            LoginLockId key = new LoginLockId(inv.getArgument(0), inv.getArgument(1));
            LoginLockEntity row = table.get(key);
            if (row != null && row.getLockedUntil() != null && !row.getLockedUntil().isAfter(inv.getArgument(2))) {
                table.remove(key);
                return 1;
            }
            return 0;
        });
        when(repository.deleteStale(any(LocalDateTime.class), any(LocalDateTime.class))).thenAnswer(inv -> {
            LocalDateTime time = inv.getArgument(0);
            LocalDateTime now = inv.getArgument(1);
            int before = table.size();
            table.values().removeIf(r -> r.getUpdatedAt().isBefore(time)
                    && (r.getLockedUntil() == null || !r.getLockedUntil().isAfter(now)));
            return before - table.size();
        });
    }

    private void fail(String id, String ip, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(id, ip);
        }
    }

    @Test
    @DisplayName("4번 실패까지는 잠기지 않는다")
    void fourFailuresNotLocked() {
        fail("alice", "1.1.1.1", 4);

        assertThat(service.isLocked("alice", "1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("5번째 실패에 잠기고 해제 시각은 지금 + 10분이다")
    void fifthFailureLocks() {
        fail("alice", "1.1.1.1", 5);

        assertThat(service.isLocked("alice", "1.1.1.1")).isTrue();
        assertThat(table.get(new LoginLockId("alice", "1.1.1.1")).getLockedUntil()).isEqualTo(START.plusMinutes(10));
    }

    @Test
    @DisplayName("10분이 지나면 자동으로 해제되고, 해제 시각과 같은 순간부터 풀린다")
    void unlocksAfterTenMinutes() {
        fail("alice", "1.1.1.1", 5);

        clock.plusMinutes(9);
        assertThat(service.isLocked("alice", "1.1.1.1")).isTrue();
        clock.plusMinutes(1);
        assertThat(service.isLocked("alice", "1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("해제된 뒤에는 횟수를 처음부터 다시 센다(한 번 틀렸다고 바로 다시 잠기지 않는다)")
    void countRestartsAfterUnlock() {
        fail("alice", "1.1.1.1", 5);
        clock.plusMinutes(10);

        fail("alice", "1.1.1.1", 1);
        assertThat(service.isLocked("alice", "1.1.1.1")).isFalse();
        assertThat(table.get(new LoginLockId("alice", "1.1.1.1")).getFailCount()).isEqualTo(1);

        fail("alice", "1.1.1.1", 4);
        assertThat(service.isLocked("alice", "1.1.1.1")).isTrue();
    }

    @Test
    @DisplayName("성공하면 실패 기록이 지워져 다시 처음부터 센다")
    void successResets() {
        fail("alice", "1.1.1.1", 4);
        service.recordSuccess("alice", "1.1.1.1");

        assertThat(table).isEmpty();
        fail("alice", "1.1.1.1", 4);
        assertThat(service.isLocked("alice", "1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("없는 아이디도 똑같이 세고 잠근다")
    void unknownIdIsCountedToo() {
        fail("nobody", "1.1.1.1", 5);

        assertThat(service.isLocked("nobody", "1.1.1.1")).isTrue();
    }

    @Test
    @DisplayName("다른 IP·다른 아이디는 따로 센다")
    void separateKeys() {
        fail("alice", "1.1.1.1", 5);

        assertThat(service.isLocked("alice", "2.2.2.2")).isFalse();
        assertThat(service.isLocked("bob", "1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("20자를 넘는 아이디는 앞 20자로 줄여 기록하고 같은 키로 잠긴다")
    void longIdIsCut() {
        String longId = "a".repeat(30);
        fail(longId, "1.1.1.1", 5);

        assertThat(table.keySet()).containsExactly(new LoginLockId("a".repeat(20), "1.1.1.1"));
        assertThat(service.isLocked(longId, "1.1.1.1")).isTrue();
        assertThat(service.isLocked("a".repeat(20), "1.1.1.1")).isTrue();
    }

    @Test
    @DisplayName("IP는 45자까지만, 값이 없으면 빈 문자열로 기록한다")
    void ipCutAndNull() {
        service.recordFailure("alice", "9".repeat(60));
        service.recordFailure(null, null);

        assertThat(table.keySet()).containsExactlyInAnyOrder(
                new LoginLockId("alice", "9".repeat(45)), new LoginLockId("", ""));
    }

    @Test
    @DisplayName("동시에 행을 만들다 충돌하면 그 행의 횟수를 올린다(실패가 사라지지 않는다)")
    void insertRaceIsRetried() {
        LoginLockId key = new LoginLockId("alice", "1.1.1.1");
        // 첫 UPDATE는 행이 없다고 보였지만(0), INSERT 때는 다른 요청이 먼저 만든 상태
        when(repository.saveAndFlush(any(LoginLockEntity.class))).thenAnswer(inv -> {
            table.put(key, LoginLockEntity.builder().loginId("alice").clientIp("1.1.1.1")
                    .failCount(1).updatedAt(START).build());
            throw new DataIntegrityViolationException("duplicate");
        });

        service.recordFailure("alice", "1.1.1.1");

        assertThat(table.get(key).getFailCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("오래된 미잠금 행만 정리하고, 잠금 중인 행과 최근 행은 남긴다")
    void purgeStale() {
        fail("old", "1.1.1.1", 2);
        fail("locked", "1.1.1.1", 5);
        clock.plusMinutes(60 * 24 + 1);
        fail("recent", "1.1.1.1", 1);
        // 잠금 중인 행은 해제 시각이 아직 안 지난 것으로 만든다
        table.get(new LoginLockId("locked", "1.1.1.1")).setLockedUntil(LocalDateTime.now(clock).plusMinutes(5));

        service.purgeStale();

        assertThat(table.keySet()).extracting(LoginLockId::getLoginId).containsExactlyInAnyOrder("locked", "recent");
    }

}
