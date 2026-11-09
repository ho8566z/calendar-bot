package com.office.calendar.admin;

import com.office.calendar.member.jpa.AuthorityEntity;
import com.office.calendar.member.jpa.AuthorityRepository;
import com.office.calendar.member.jpa.MemberEntity;
import com.office.calendar.member.jpa.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 서버가 켜질 때 관리자(ADMIN) 계정이 없으면 환경변수 값으로 1개 만든다(adm-005)
@Slf4j
@Service
public class AdminSeedService {

    public static final String ROLE_ADMIN = "ADMIN";

    private static final int ID_MAX_LENGTH = 20;        // USER_MEMBER.ID 길이
    private static final int MAIL_MAX_LENGTH = 100;     // USER_MEMBER.MAIL 길이
    private static final String PLACEHOLDER = "-";      // MAIL·PHONE은 NOT NULL이라 비어 있으면 채운다

    final private MemberRepository memberRepository;
    final private AuthorityRepository authorityRepository;
    final private PasswordEncoder passwordEncoder;

    final private String adminId;
    final private String adminPassword;
    final private String adminMail;

    public AdminSeedService(MemberRepository memberRepository,
                            AuthorityRepository authorityRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${app.admin.id:}") String adminId,
                            @Value("${app.admin.password:}") String adminPassword,
                            @Value("${app.admin.mail:}") String adminMail) {
        this.memberRepository = memberRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminId = adminId;
        this.adminPassword = adminPassword;
        this.adminMail = adminMail;
    }

    // 관리자 계정을 새로 만들었으면 true, 건너뛰었으면 false
    @Transactional
    public boolean seedIfAbsent() {
        log.info("seedIfAbsent()");

        if (memberRepository.existsByAuthorityEntity_AuthRoleName(ROLE_ADMIN)) {
            log.info("관리자 계정이 이미 있어 만들지 않는다");
            return false;
        }

        if (isBlank(adminId) || isBlank(adminPassword)) {
            log.warn("ADMIN_ID, ADMIN_PASSWORD 환경변수가 없어 관리자 계정을 만들지 않는다");
            return false;
        }

        String id = adminId.trim();
        if (id.length() > ID_MAX_LENGTH) {
            log.warn("ADMIN_ID가 {}자를 넘어 관리자 계정을 만들지 않는다", ID_MAX_LENGTH);
            return false;
        }

        String mail = isBlank(adminMail) ? PLACEHOLDER : adminMail.trim();
        if (mail.length() > MAIL_MAX_LENGTH) {
            log.warn("ADMIN_MAIL이 {}자를 넘어 관리자 계정을 만들지 않는다", MAIL_MAX_LENGTH);
            return false;
        }

        // 같은 아이디의 일반 회원을 몰래 관리자로 바꾸지 않는다
        if (memberRepository.existsByMemId(id)) {
            log.warn("ADMIN_ID와 같은 아이디의 회원이 이미 있어 관리자 계정을 만들지 않는다");
            return false;
        }

        AuthorityEntity adminRole = authorityRepository.findByAuthRoleName(ROLE_ADMIN).orElse(null);
        if (adminRole == null) {
            log.warn("ADMIN 역할이 없어 관리자 계정을 만들지 않는다");
            return false;
        }

        MemberEntity admin = MemberEntity.builder()
                .memId(id)
                .memPw(passwordEncoder.encode(adminPassword))
                .memMail(mail)
                .memPhone(PLACEHOLDER)
                .authorityEntity(adminRole)
                .build();
        memberRepository.save(admin);

        log.info("관리자 계정을 만들었다: {}", id);
        return true;

    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
