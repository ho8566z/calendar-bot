package com.office.calendaradmin.user.jpa;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "USER_AUTHORITY", uniqueConstraints = {@UniqueConstraint(columnNames = "ROLE_NAME")})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAuthorityEntity {

    @Id
    @Column(name="NO")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private byte authNo;                // 권한 고유 번호

    @Column(name="ROLE_NAME", nullable = false, length = 20)
    private String authRoleName;        // 권한 이름

    public UserAuthorityDto toDto() {
        return UserAuthorityDto.builder()
                .no(authNo)
                .role_name(authRoleName)
                .build();
    }

}
