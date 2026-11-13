package com.office.calendar.admin;

import lombok.Builder;

/**
 * 관리자 화면의 사용자 목록 한 줄. 비밀번호(해시 포함)는 일부러 담지 않는다.
 * JSON 필드 이름(reg_date, mod_date)은 화면 JS가 쓰는 계약이라 그대로 둔다.
 *
 * @param no       회원 번호
 * @param id       아이디
 * @param mail     메일
 * @param phone    연락처
 * @param role     역할 이름(PRE_USER, USER, ADMIN)
 * @param reg_date 가입 시각
 * @param mod_date 수정 시각
 */
@Builder
public record AdminUserDto(int no, String id, String mail, String phone, String role,
                           String reg_date, String mod_date) {
}
