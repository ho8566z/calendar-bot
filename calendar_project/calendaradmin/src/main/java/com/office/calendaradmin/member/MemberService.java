package com.office.calendaradmin.member;

import com.office.calendaradmin.member.jpa.AuthorityEntity;
import com.office.calendaradmin.member.jpa.MemberEntity;
import com.office.calendaradmin.member.jpa.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
//@RequiredArgsConstructor
public class MemberService {

    final public static int USER_ID_ALREADY_EXIST   = 0;
    final public static int USER_SIGNUP_SUCCESS     = 1;
    final public static int USER_SIGNUP_FAIL        = -1;

    final public static int NEW_PASSWORD_CREATION_SUCCESS   = 1;
    final public static int NEW_PASSWORD_CREATION_FAIL      = 0;

    final private PasswordEncoder passwordEncoder;
    final private JavaMailSender javaMailSender;
    final private MemberRepository memberRepository;


    public MemberService(PasswordEncoder passwordEncoder,
                         JavaMailSender javaMailSender,
                         MemberRepository memberRepository) {
        this.passwordEncoder = passwordEncoder;
        this.javaMailSender = javaMailSender;
        this.memberRepository = memberRepository;
    }


    @Transactional
    public int signupConfirm(MemberDto memberDto) {
        log.info("signupConfirm()");

        boolean isMember = memberRepository.existsByMemId(memberDto.getId());

        if (!isMember) {
            String encodedPW = passwordEncoder.encode(memberDto.getPw());
            memberDto.setPw(encodedPW);

            MemberEntity savedMemberEntity = memberRepository.save(memberDto.toEntity());

            if (savedMemberEntity != null) {
                if (savedMemberEntity.getMemId().equals("superadmin")) {
                    savedMemberEntity.setAuthorityEntity(AuthorityEntity.builder()
                            .authNo((byte) 3)
                            .authRoleName("SUPER_ADMIN")
                            .build());
                }
                return USER_SIGNUP_SUCCESS;
            } else
                return USER_SIGNUP_FAIL;

        } else {
            return USER_ID_ALREADY_EXIST;
        }

    }

    public int findpasswordConfirm(MemberDto memberDto) {
        log.info("findpasswordConfirm()");

        Optional<MemberEntity> optionalMember =
                memberRepository.findByMemIdAndMemMail(memberDto.getId(), memberDto.getMail());
        if (optionalMember.isPresent()) {
            String newPassword = createNewPassword();
            MemberEntity findedMemberEntity = optionalMember.get();
            findedMemberEntity.setMemPw(passwordEncoder.encode(newPassword));

            MemberEntity updateMember = memberRepository.save(findedMemberEntity);
            if (updateMember != null) {
                sendNewPasswordByMail(memberDto.getMail(), newPassword);

                return NEW_PASSWORD_CREATION_SUCCESS;

            }
            return NEW_PASSWORD_CREATION_FAIL;

        }
        return 0;       // 여기 수정?
    }

    private String createNewPassword() {
        log.info("createNewPassword()");

        char[] chars = new char[] {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
                'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
                'u', 'v', 'w', 'x', 'y', 'z'
        };

        StringBuffer stringBuffer = new StringBuffer();
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.setSeed(new Date().getTime());

        int index = 0;
        int length = chars.length;
        for (int i = 0; i < 8; i++) {
            index = secureRandom.nextInt(length);

            if (index % 2 == 0)
                stringBuffer.append(String.valueOf(chars[index]).toUpperCase());
            else
                stringBuffer.append(String.valueOf(chars[index]).toLowerCase());
        }

        log.info("NEW PASSWORD: {}", stringBuffer.toString());

        return stringBuffer.toString();

    }

    private void sendNewPasswordByMail(String toMailAddr, String newPassword) {
        log.info("sendNewPasswordByMail()");

        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setTo("nikecafe@naver.com");
        simpleMailMessage.setSubject("[MyCalendar] 새 비밀번호 안내입니다.");
        simpleMailMessage.setText("새 비밀번호: " + newPassword);
        simpleMailMessage.setFrom("hohasic@gmail.com");

        javaMailSender.send(simpleMailMessage);

    }

}
