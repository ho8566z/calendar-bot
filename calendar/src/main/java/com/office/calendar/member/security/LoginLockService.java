package com.office.calendar.member.security;

import com.office.calendar.member.jpa.LoginLockEntity;
import com.office.calendar.member.jpa.LoginLockId;
import com.office.calendar.member.jpa.LoginLockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

// 로그인 시도 제한(sec-006, FNC-S-003). 같은 아이디를 같은 IP에서 연속 maxFail회 틀리면 lockMinutes분 동안 거부한다.
// 없는 아이디도 똑같이 세어 존재 여부를 드러내지 않는다. 아이디·비밀번호·IP는 로그에 남기지 않는다.
@Slf4j
@Service
public class LoginLockService {

    static final int LOGIN_ID_MAX = 20;
    static final int CLIENT_IP_MAX = 45;

    // 미잠금 행을 이 시간이 지나면 정리한다
    static final Duration STALE_AFTER = Duration.ofHours(24);

    private final LoginLockRepository repository;
    private final Clock clock;
    private final int maxFail;
    private final int lockMinutes;

    public LoginLockService(LoginLockRepository repository, Clock clock,
                            @Value("${login.max-fail:5}") int maxFail,
                            @Value("${login.lock-minutes:10}") int lockMinutes) {
        this.repository = repository;
        this.clock = clock;
        this.maxFail = maxFail;
        this.lockMinutes = lockMinutes;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    // 지금 (아이디, IP)가 잠겨 있는지. 해제 시각이 지났으면 잠기지 않은 것이다
    public boolean isLocked(String loginId, String clientIp) {
        return repository.findById(new LoginLockId(normalizeId(loginId), normalizeIp(clientIp)))
                .map(lock -> lock.isLocked(now()))
                .orElse(false);
    }

    // 로그인 실패 기록: 횟수를 1 올리고 maxFail에 이르면 lockMinutes분 뒤까지 잠근다
    public void recordFailure(String loginId, String clientIp) {
        String id = normalizeId(loginId);
        String ip = normalizeIp(clientIp);
        LocalDateTime now = now();

        // 잠금이 풀린 행은 지워 횟수를 처음부터 다시 센다
        repository.deleteExpiredLock(id, ip, now);

        if (repository.increaseFailCount(id, ip, now) == 0) {
            try {
                repository.saveAndFlush(LoginLockEntity.builder()
                        .loginId(id).clientIp(ip).failCount(1).updatedAt(now).build());
            } catch (DataIntegrityViolationException e) {
                // 동시에 다른 요청이 먼저 행을 만들었다. 그 행의 횟수를 올린다
                repository.increaseFailCount(id, ip, now);
            }
        }

        if (repository.lock(id, ip, maxFail, now.plusMinutes(lockMinutes)) > 0) {
            log.info("login locked");
        }
    }

    // 로그인 성공 시 실패 기록 삭제
    public void recordSuccess(String loginId, String clientIp) {
        repository.deleteByKey(normalizeId(loginId), normalizeIp(clientIp));
    }

    // 오래된 미잠금 행 정리(매일 03:30)
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void purgeStale() {
        LocalDateTime now = now();
        int deleted = repository.deleteStale(now.minus(STALE_AFTER), now);
        log.info("login lock purge, deleted={}", deleted);
    }

    // 컬럼 길이(20)를 넘는 아이디는 앞 20자로 줄인다(길이만 다른 입력으로 우회하거나 INSERT가 실패하지 않게)
    static String normalizeId(String loginId) {
        return cut(loginId, LOGIN_ID_MAX);
    }

    static String normalizeIp(String clientIp) {
        return cut(clientIp, CLIENT_IP_MAX);
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

}
