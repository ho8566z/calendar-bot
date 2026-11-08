package com.office.calendar.brief.jpa;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 주간 브리핑 발송 결과(brf-004). 실패 원인 확인과 같은 주 중복 발송 방지에 쓴다.
// (MEMBER_NO, WEEK_START)가 유니크라 같은 회원·같은 주의 기록은 하나뿐이다.
@Entity
@Table(name = "BRIEF_LOG", uniqueConstraints = {@UniqueConstraint(columnNames = {"MEMBER_NO", "WEEK_START"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BriefLogEntity {

    public static final int ERROR_MSG_MAX = 255;

    @Id
    @Column(name = "NO")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int no;

    @Column(name = "MEMBER_NO", nullable = false)
    private int memberNo;

    // 브리핑 대상 주의 월요일
    @Column(name = "WEEK_START", nullable = false)
    private LocalDate weekStart;

    @Column(name = "SENT_AT")
    private LocalDateTime sentAt;

    // 문자열로 저장한다. columnDefinition 을 따로 두지 않으면 Hibernate 가 MySQL ENUM 컬럼으로 만들어,
    // 결과 값을 추가할 때 ddl-auto=update 가 ENUM 값을 바꾸지 못해 INSERT 가 실패한다(명세서도 VARCHAR(10)).
    @Enumerated(EnumType.STRING)
    @Column(name = "RESULT", nullable = false, length = 10, columnDefinition = "VARCHAR(10)")
    private BriefResult result;

    // 실패 사유. 봇 토큰·요청 URL·메시지 본문은 넣지 않는다
    @Column(name = "ERROR_MSG", length = ERROR_MSG_MAX)
    private String errorMsg;

}
