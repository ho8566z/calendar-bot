package com.office.calendaradmin.user.jpa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAuthorityDto {

    private byte no;             // 권한 고유 번호
    private String role_name;    // 권한 이름

    public UserAuthorityEntity toEntity() {
        return UserAuthorityEntity.builder()
                .authNo(no)
                .authRoleName(role_name)
                .build();
    }

}
