package com.office.calendaradmin.member.security;

import com.office.calendaradmin.member.jpa.MemberEntity;
import com.office.calendaradmin.member.jpa.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class MemberDetailsService implements UserDetailsService {

    final private MemberRepository memberRepository;

    public MemberDetailsService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("[MemberDetailsService] loadUserByUsername()");

        Optional<MemberEntity> optionalMember = memberRepository.findByMemId(username);
        if (optionalMember.isPresent()) {
            MemberEntity findMemberEntity = optionalMember.get();

            if (findMemberEntity.getAuthorityEntity().getAuthNo() == 1) {
                return null;
            }

            return User.builder()
                    .username(findMemberEntity.getMemId())
                    .password(findMemberEntity.getMemPw())
                    .roles(findMemberEntity.getAuthorityEntity().getAuthRoleName())
                    .build();
        }

        return null;
    }

}
