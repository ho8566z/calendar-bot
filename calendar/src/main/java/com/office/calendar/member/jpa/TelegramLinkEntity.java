package com.office.calendar.member.jpa;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 텔레그램 연동 코드(brf-005). 회원이 발급받은 1회용 코드를 봇에서 확인하면 chat ID를 저장하고 이 행을 지운다.
// 회원당 코드는 1개만 둔다(새로 발급하면 이전 코드를 대체). 코드는 로그에 남기지 않는다.
@Entity
@Table(name = "TELEGRAM_LINK", uniqueConstraints = {@UniqueConstraint(columnNames = "MEMBER_NO")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramLinkEntity {

    @Id
    @Column(name = "CODE", length = 64)
    private String code;

    // 코드를 발급한 회원 번호(USER_MEMBER.NO). 일정의 OWNER_ID처럼 값으로만 가리킨다
    @Column(name = "MEMBER_NO", nullable = false)
    private int memberNo;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 지금 시각 기준으로 만료됐는지(만료 시각과 같은 순간부터 만료)
    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

}
