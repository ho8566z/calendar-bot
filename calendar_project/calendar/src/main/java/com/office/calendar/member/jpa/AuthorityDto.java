package com.office.calendar.member.jpa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Bean;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorityDto {

    private byte no;
    private String role_name;

    public AuthorityEntity toEntity() {

        return AuthorityEntity.builder()
                .authNo(no)
                .authRoleName(role_name)
                .build();
    }

}
