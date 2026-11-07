package com.office.calendar.member;

import com.office.calendar.member.jpa.AuthorityDto;
import com.office.calendar.member.jpa.MemberEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberDto {

    private int no;
    private String id;
    private String pw;
    private String mail;
    private String phone;
//    private int authority_no;
    private AuthorityDto authorityDto;
    private String reg_date;
    private String mod_date;

    public MemberEntity toEntity() {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return MemberEntity.builder()
                .memNo(no)
                .memId(id)
                .memPw(pw)
                .memMail(mail)
                .memPhone(phone)
//                .memAuthorityNo(authority_no)
                .authorityEntity(authorityDto != null ? authorityDto.toEntity() : null)
                .memRegDate(reg_date != null ? LocalDateTime.parse(reg_date,formatter) : null)
                .memModDate(mod_date != null ? LocalDateTime.parse(mod_date,formatter) : null)
                .build();

    }

}
