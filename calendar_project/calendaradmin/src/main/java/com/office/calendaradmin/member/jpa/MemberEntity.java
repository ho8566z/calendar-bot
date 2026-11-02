package com.office.calendaradmin.member.jpa;

import com.office.calendaradmin.member.MemberDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "ADMIN_MEMBER", uniqueConstraints = {@UniqueConstraint(columnNames = "ID")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberEntity {

    @Id
    @Column(name = "NO")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int memNo;

    @Column(name = "ID", nullable = false, length = 20)
    private String memId;

    @Column(name = "PW", nullable = false, length = 100)
    private String memPw;

    @Column(name = "MAIL", nullable = false, length = 20)
    private String memMail;

    @Column(name = "PHONE", nullable = false, length = 20)
    private String memPhone;

    @ManyToOne
    @JoinColumn(name = "AUTHORITY_NO")
    private AuthorityEntity authorityEntity;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime memRegDate;

    @Column(name = "MOD_DATE")
    private LocalDateTime memModDate;

    @PrePersist
    protected void onCreate() {
        this.authorityEntity = new AuthorityEntity((byte) 1, "PRE_ADMIN");
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
                .authorityDto(authorityEntity.toDto())
                .reg_date(memRegDate != null ? memRegDate.format(formatter) : null)
                .mod_date(memModDate != null ? memModDate.format(formatter) : null)
                .build();

    }

}
