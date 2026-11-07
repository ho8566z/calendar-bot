package com.office.calendaradmin.member.mapper;

import com.office.calendaradmin.member.MemberDto;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MemberMapper {

    // @Select("SELECT COUNT(*) FROM USER_MEMBER WHERE ID = #{id}")
    public boolean isMember(@Param("memId") String id);

    // @Insert("INSERT INTO USER_MEMBER(ID, PW, MAIL, PHONE) VALUES(#{id}, #{pw}, #{mail}, #{phone})")
    public int insertMember(MemberDto memberDto);

    // @Select("SELECT * FROM USER_MEMBER WHERE ID = #{id}")
    public MemberDto selectMemberByID(@Param("memId") String id);

    // @Update("UPDATE USER_MEMBER SET PW = #{pw}, MAIL = #{mail}, PHONE = #{phone} WHERE NO = #{no}")
    public int updateMember(MemberDto memberDto);

    // @Select("SELECT * FROM USER_MEMBER WHERE ID=#{id} AND MAIL=#{mail}")
    public MemberDto selectMemberByIDAndMail(MemberDto memberDto);

    // @Update("UPDATE USER_MEMBER SET PW = #{memPw} WHERE ID = #{memId}")
    public int updatePassword(@Param("memId") String id, @Param("memPw") String encodedNewPw);

}
