package com.office.calendar.member.security;

import com.office.calendar.member.jpa.LoginLockEntity;
import com.office.calendar.member.jpa.LoginLockId;
import com.office.calendar.member.jpa.LoginLockRepository;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// 테스트용: 리포지토리를 메모리 Map으로 흉내 내 DB의 동작(원자적 +1, 잠금, 삭제)을 재현한다.
public final class InMemoryLoginLockRepository {

    private InMemoryLoginLockRepository() {
    }

    public static LoginLockRepository create(Map<LoginLockId, LoginLockEntity> table) {
        LoginLockRepository repository = Mockito.mock(LoginLockRepository.class);
        stub(repository, table);
        return repository;
    }

    public static void stub(LoginLockRepository repository, Map<LoginLockId, LoginLockEntity> table) {
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

}
