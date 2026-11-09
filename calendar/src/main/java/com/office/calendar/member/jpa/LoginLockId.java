package com.office.calendar.member.jpa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// LOGIN_LOCK의 복합 키(아이디 + IP)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginLockId implements Serializable {

    private String loginId;

    private String clientIp;

}
