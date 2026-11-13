package com.office.calendar.member.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 로그인 시도 제한(sec-006). 같은 아이디를 같은 IP에서 연속으로 틀린 횟수를 센다.
// 없는 아이디도 같은 방식으로 기록해 아이디 존재 여부를 드러내지 않는다. 성공하면 행을 지운다.
@Entity
@Table(name = "LOGIN_LOCK")
@IdClass(LoginLockId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginLockEntity {

    @Id
    @Column(name = "LOGIN_ID", length = 20, nullable = false)
    private String loginId;

    // IPv6 길이까지. IP를 키에 넣어 남이 일부러 틀려도 다른 IP의 사용자는 잠기지 않게 한다
    @Id
    @Column(name = "CLIENT_IP", length = 45, nullable = false)
    private String clientIp;

    @Column(name = "FAIL_COUNT", nullable = false)
    private int failCount;

    // 이 시각 전까지 로그인을 거부한다(없으면 잠기지 않은 상태)
    @Column(name = "LOCKED_UNTIL", columnDefinition = "DATETIME(6) NULL")
    private LocalDateTime lockedUntil;

    @Column(name = "UPDATED_AT", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    // 지금 시각 기준으로 잠겨 있는지(해제 시각과 같은 순간부터 해제)
    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

}
