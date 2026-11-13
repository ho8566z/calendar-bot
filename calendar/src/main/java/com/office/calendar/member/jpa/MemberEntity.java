package com.office.calendar.member.jpa;

import com.office.calendar.member.MemberDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "USER_MEMBER", uniqueConstraints = {
        @UniqueConstraint(columnNames = "ID"),
        @UniqueConstraint(columnNames = "TELEGRAM_CHAT_ID")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberEntity {

    @Id
    @Column(name = "NO")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int memNo;

//    @Column(name = "ID", nullable = false, length = 20, unique = true)
    @Column(name = "ID", nullable = false, length = 20)
    private String memId;

    @Column(name = "PW", nullable = false, length = 100)
    private String memPw;

    @Column(name = "MAIL", nullable = false, length = 100)
    private String memMail;

    @Column(name = "PHONE", nullable = false, length = 20)
    private String memPhone;

    /*
    @Column(name = "AUTHORITY_NO")
    private int memAuthorityNo;
     */

    @ManyToOne
    @JoinColumn(name = "AUTHORITY_NO")
    private AuthorityEntity authorityEntity;

    // 텔레그램 chat ID(brf-005). 연동 전·해제 후에는 NULL이고, 한 chat ID는 한 계정에만 연결된다.
    @Column(name = "TELEGRAM_CHAT_ID")
    private Long telegramChatId;

    // 주간 브리핑 수신 여부(brf-003). 기본 끔이며 텔레그램이 연동된 회원만 켤 수 있다(서비스 규칙).
    @Column(name = "BRIEF_YN", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean briefYn;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime memRegDate;

    @Column(name = "MOD_DATE")
    private LocalDateTime memModDate;

    @PrePersist
    protected void onCreate() {
//        this.memAuthorityNo = 1;
        // 권한이 정해지지 않은 새 회원만 PRE_USER로 시작한다(관리자 시드처럼 미리 정한 권한은 그대로 둔다)
        if (this.authorityEntity == null) {
            this.authorityEntity = new AuthorityEntity((byte) 1, "PRE_USER");
        }
        this.memRegDate = LocalDateTime.now();
        this.memModDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.memModDate = LocalDateTime.now();
    }

    public MemberDto toDto() {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return MemberDto.builder()
                .no(memNo)
                .id(memId)
                .pw(memPw)
                .mail(memMail)
                .phone(memPhone)
//                .authority_no(memAuthorityNo)
                .authorityDto(authorityEntity.toDto())
                .reg_date(memRegDate != null ? memRegDate.format(formatter) : null)
                .mod_date(memModDate != null ? memModDate.format(formatter) : null)
                .build();

    }

}
