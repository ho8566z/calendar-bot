package com.office.calendaradmin.member;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class MemberDao {

    final private String CLASS_NAME = "[MemberDao] ";

    final private JdbcTemplate jdbcTemplate;

    public MemberDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isMember(String id) {
        System.out.println(CLASS_NAME.concat("isMember()"));

        String sql = "SELECT COUNT(*) FROM USER_MEMBER WHERE ID = ?";

        int result = jdbcTemplate.queryForObject(sql, Integer.class, id);
        if (result > 0)
            return true;
        else
            return false;

    }

    public int insertMember(MemberDto memberDto) {
        System.out.println(CLASS_NAME.concat("insertMember()"));

        String sql = "INSERT INTO USER_MEMBER(ID, PW, MAIL, PHONE) " +
                "VALUES(?, ?, ?, ?)";

        int result = -1;
        try {
            result = jdbcTemplate.update(sql,
                    memberDto.getId(),
                    memberDto.getPw(),
                    memberDto.getMail(),
                    memberDto.getPhone());
        } catch (Exception e) {
            e.printStackTrace();

        }

        return result;

    }

    public MemberDto selectMemberByID(String id) {
        System.out.println(CLASS_NAME.concat("isMember()"));

        String sql = "SELECT * FROM USER_MEMBER WHERE ID = ?";

        List<MemberDto> memberDtos = new ArrayList<>();

        try {
            RowMapper<MemberDto> rowMapper = BeanPropertyRowMapper.newInstance(MemberDto.class);
            memberDtos = jdbcTemplate.query(sql, rowMapper, id);

        } catch (DataAccessException e) {
            e.printStackTrace();

        } catch (Exception e) {
            e.printStackTrace();

        }

        return memberDtos.size() > 0 ? memberDtos.get(0) : null;

    }

    public int updateMember(MemberDto memberDto) {
        System.out.println(CLASS_NAME.concat("updateMember()"));

        String sql =    "UPDATE " +
                "USER_MEMBER " +
                "SET " +
                "PW = ?, " +
                "MAIL = ?, " +
                "PHONE = ? " +
                "WHERE " +
                "NO = ?";

        int result = -1;
        try {
            result = jdbcTemplate.update(sql,
                    memberDto.getPw(),
                    memberDto.getMail(),
                    memberDto.getPhone(),
                    memberDto.getNo());

        } catch (Exception e) {
            e.printStackTrace();

        }

        return result;

    }

    public MemberDto selectMemberByIDAndMail(MemberDto memberDto) {
        System.out.println(CLASS_NAME.concat("selectMemberByIDAndMail()"));

        String sql = "SELECT " +
                "* " +
                "FROM " +
                "USER_MEMBER " +
                "WHERE " +
                "ID=? AND MAIL=?";

        List<MemberDto> memberDtos = new ArrayList<>();
        try {
            RowMapper<MemberDto> rowMapper = BeanPropertyRowMapper.newInstance(MemberDto.class);
            memberDtos = jdbcTemplate.query(sql, rowMapper, memberDto.getId(), memberDto.getMail());

        } catch (Exception e) {
            e.printStackTrace();

        }

        return memberDtos.size() > 0 ? memberDtos.get(0) : null;

    }

    public int updatePassword(String id, String encodedNewPw) {
        System.out.println(CLASS_NAME.concat("updatePassword()"));

        String sql = "UPDATE USER_MEMBER SET PW = ? WHERE ID = ?";

        int result = -1;
        try {
            result = jdbcTemplate.update(sql, encodedNewPw, id);

        } catch (Exception e) {
            e.printStackTrace();

        }

        return result;

    }
}
