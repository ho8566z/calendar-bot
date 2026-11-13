package com.office.calendar.member;

import org.jspecify.annotations.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class MemberDao {

    final private JdbcTemplate jdbcTemplate;

    public MemberDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isMember(String id) {
        log.debug("isMember()");

        String sql = "SELECT COUNT(*) FROM USER_MEMBER WHERE ID = ?";

        int result = jdbcTemplate.queryForObject(sql, Integer.class, id);
        if (result > 0)
            return true;
        else
            return false;

    }

    public int insertMember(MemberDto memberDto) {
        log.debug("insertMember()");

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
            log.error("insertMember 실패", e);

        }

        return result;

    }

    public MemberDto selectMemberByID(String id) {
        log.debug("selectMemberByID()");

        String sql = "SELECT * FROM USER_MEMBER WHERE ID = ?";

        List<MemberDto> memberDtos = new ArrayList<>();

        try {
            RowMapper<MemberDto> rowMapper = BeanPropertyRowMapper.newInstance(MemberDto.class);
            memberDtos = jdbcTemplate.query(sql, rowMapper, id);

        } catch (DataAccessException e) {
            log.error("selectMemberByID 실패", e);

        } catch (Exception e) {
            log.error("selectMemberByID 실패", e);

        }

        return memberDtos.size() > 0 ? memberDtos.get(0) : null;

    }

    public int updateMember(MemberDto memberDto) {
        log.debug("updateMember()");

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
            log.error("updateMember 실패", e);

        }

        return result;

    }

    public MemberDto selectMemberByIDAndMail(MemberDto memberDto) {
        log.debug("selectMemberByIDAndMail()");

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
            log.error("selectMemberByIDAndMail 실패", e);

        }

        return memberDtos.size() > 0 ? memberDtos.get(0) : null;

    }

    public int updatePassword(String id, String encodedNewPw) {
        log.debug("updatePassword()");

        String sql = "UPDATE USER_MEMBER SET PW = ? WHERE ID = ?";

        int result = -1;
        try {
            result = jdbcTemplate.update(sql, encodedNewPw, id);

        } catch (Exception e) {
            log.error("updatePassword 실패", e);

        }

        return result;

    }
}
