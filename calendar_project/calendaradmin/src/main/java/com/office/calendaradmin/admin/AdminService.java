package com.office.calendaradmin.admin;

import com.office.calendaradmin.member.MemberDto;
import com.office.calendaradmin.member.jpa.AuthorityEntity;
import com.office.calendaradmin.member.jpa.MemberEntity;
import com.office.calendaradmin.member.jpa.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    final public static int UPDATE_ADMIN_AUTHORITY_SUCCESS   = 1;
    final public static int UPDATE_ADMIN_AUTHORITY_FAIL      = 0;

    final private MemberRepository memberRepository;

    public Map<String, Object> admins() {
        log.info("admins()");

        Map<String, Object> resultMap = new HashMap<>();

        List<MemberEntity> memberEntities = memberRepository.findAll();

        List<MemberDto> admins = memberEntities.stream()
                .map(MemberEntity::toDto)
                .collect(Collectors.toUnmodifiableList());

        resultMap.put("admins", admins);

        return resultMap;

    }

    @Transactional
    public Map<String, Object> updateAdminAuthority(int adminNo, Integer authorityNo) {
        log.info("updateAdminAuthority()");

        Map<String, Object> resultMap = new HashMap<>();

        Optional<MemberEntity> optionalMember = memberRepository.findById(adminNo);
        if (optionalMember.isPresent()) {
            MemberEntity memberEntity = optionalMember.get();

            Byte targetAuthorityNo = 1;
            if (authorityNo == 2)
                targetAuthorityNo = 2;
            else if (authorityNo == 3)
                targetAuthorityNo = 3;

            memberEntity.setAuthorityEntity(AuthorityEntity.builder()
                    .authNo(targetAuthorityNo)
                    .build());

            // flush: DB에 변경 사항을 즉시 반영 하려고 할때
//            memberRepository.save(memberEntity);
            memberRepository.saveAndFlush(memberEntity);
            resultMap.put("result", UPDATE_ADMIN_AUTHORITY_SUCCESS);
            resultMap.put("mod_date", memberEntity.toDto().getMod_date());

        } else {
            resultMap.put("result", UPDATE_ADMIN_AUTHORITY_FAIL);

        }

        return resultMap;

    }
}
