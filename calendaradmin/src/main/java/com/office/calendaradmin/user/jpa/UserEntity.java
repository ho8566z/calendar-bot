package com.office.calendaradmin.user.jpa;

import com.office.calendaradmin.user.UserDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "USER_MEMBER", uniqueConstraints = {@UniqueConstraint(columnNames = "ID")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @Column(name = "NO")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int memNo;                 // 사용자 고유 번호

    @Column(name = "ID", nullable = false, length = 20)
    private String memId;              // 사용자 아이디

    @Column(name = "PW", nullable = false, length = 100)
    private String memPw;              // 사용자 비밀번호

    @Column(name = "MAIL", nullable = false, length = 20)
    private String memMail;            // 사용자 메일

    @Column(name = "PHONE", nullable = false, length = 20)
    private String memPhone;           // 사용자 연락처

    /*
    @Column(name = "AUTHORITY_NO")
    private int memAuthorityNo;       // 사용자 권한 번호
    */

    @ManyToOne
    @JoinColumn(name="AUTHORITY_NO")
    private UserAuthorityEntity authorityEntity;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime memRegDate;        // 사용자 정보 등록일

    @Column(name = "MOD_DATE")
    private LocalDateTime memModDate;        // 사용자 정보 수정일

    @PrePersist
    protected void onCreate() {
//        this.memAuthorityNo = 1;
        this.authorityEntity = new UserAuthorityEntity((byte) 1, "PRE_USER");
        this.memRegDate = LocalDateTime.now();
        this.memModDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.memModDate = LocalDateTime.now();
    }

    public UserDto toDto() {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return UserDto.builder()
                .no(memNo)
                .id(memId)
                .pw(memPw)
                .mail(memMail)
                .phone(memPhone)
                // .authority_no(memAuthorityNo)
                .userAuthorityDto(authorityEntity.toDto())
                .reg_date(memRegDate != null ? memRegDate.format(formatter) : null)
                .mod_date(memModDate != null ? memModDate.format(formatter) : null)
                .build();


    }

}
