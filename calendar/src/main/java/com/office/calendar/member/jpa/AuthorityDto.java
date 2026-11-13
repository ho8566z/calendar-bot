package com.office.calendar.member.jpa;

import lombok.Builder;

@Builder
public record AuthorityDto(byte no, String role_name) {

    public AuthorityEntity toEntity() {
        return AuthorityEntity.builder()
                .authNo(no)
                .authRoleName(role_name)
                .build();
    }
}
