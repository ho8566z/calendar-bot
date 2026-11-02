package com.office.calendar.member.jpa;

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
public class AuthorityEntity {

    @Id
    @Column(name = "NO")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private byte authNo;

    @Column(name = "ROLE_NAME", nullable = false, length = 20)
    private String authRoleName;

    public AuthorityDto toDto() {
        return AuthorityDto.builder()
                .no(authNo)
                .role_name(authRoleName)
                .build();
    }

}
