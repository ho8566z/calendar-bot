package com.office.calendar.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 관리자 화면의 사용자 목록 한 줄. 비밀번호(해시 포함)는 일부러 담지 않는다.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserDto {

    private int no;             // 회원 번호
    private String id;          // 아이디
    private String mail;        // 메일
    private String phone;       // 연락처
    private String role;        // 역할 이름(PRE_USER, USER, ADMIN)
    private String reg_date;    // 가입 시각
    private String mod_date;    // 수정 시각

}
