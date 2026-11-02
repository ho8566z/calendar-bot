package com.office.calendar.member;

import com.office.calendar.member.jpa.MemberEntity;
import com.office.calendar.member.jpa.MemberRepository;
import com.office.calendar.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
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

    final private String CLASS_NAME = "[MemberService] ";

    final public static int USER_ID_ALREADY_EXIST   = 0;
    final public static int USER_SIGNUP_SUCCESS     = 1;
    final public static int USER_SIGNUP_FAIL        = -1;

    final public static int MODIFY_SUCCESS          = 1;
    final public static int MODIFY_FAIL             = 0;

    final public static int NEW_PASSWORD_CREATION_SUCCESS   = 1;
    final public static int NEW_PASSWORD_CREATION_FAIL      = 0;

    final private MemberDao memberDao;
    final private PasswordEncoder passwordEncoder;
    final private JavaMailSender javaMailSender;
    final private MemberMapper memberMapper;
    final private MemberRepository memberRepository;


    public MemberService(MemberDao memberDao,
                         PasswordEncoder passwordEncoder,
                         JavaMailSender javaMailSender,
                         MemberMapper memberMapper,
                         MemberRepository memberRepository) {
        this.memberDao = memberDao;
        this.passwordEncoder = passwordEncoder;
        this.javaMailSender = javaMailSender;
        this.memberMapper = memberMapper;
        this.memberRepository = memberRepository;
    }


    public int signupConfirm(MemberDto memberDto) {
        System.out.println(CLASS_NAME.concat("signupConfirm()"));

//        boolean isMember = memberDao.isMember(memberDto.getId());
//        boolean isMember = memberMapper.isMember(memberDto.getId());
        boolean isMember = memberRepository.existsByMemId(memberDto.getId());

        if (!isMember) {
            String encodedPW = passwordEncoder.encode(memberDto.getPw());
            memberDto.setPw(encodedPW);
//            int result = memberDao.insertMember(memberDto);
//            int result = memberMapper.insertMember(memberDto);

            /*
            MemberEntity memberEntity = MemberEntity.builder()
                    .memId(memberDto.getId())
                    .memPw(memberDto.getPw())
                    .memMail(memberDto.getMail())
                    .memPhone(memberDto.getPhone())
                    .build();

            MemberEntity savedMemberEntity = memberRepository.save(memberEntity);
            */

            MemberEntity savedMemberEntity = memberRepository.save(memberDto.toEntity());

            if (savedMemberEntity != null)
                return USER_SIGNUP_SUCCESS;
            else
                return USER_SIGNUP_FAIL;
//            if (result > 0)
//                return USER_SIGNUP_SUCCESS;
//            else
//                return USER_SIGNUP_FAIL;

        } else {
            return USER_ID_ALREADY_EXIST;
        }

    }

    public String signinConfirm(MemberDto memberDto) {
        System.out.println(CLASS_NAME.concat("signinConfirm()"));

//        MemberDto dto = memberDao.selectMemberByID(memberDto.getId());
        /*
        MemberDto dto = memberMapper.selectMemberByID(memberDto.getId());
        if (dto != null && passwordEncoder.matches(memberDto.getPw(), dto.getPw())) {
            System.out.println(CLASS_NAME.concat("MEMBER LOGIN SUCCESS!!"));
            return dto.getId();

        } else {
            System.out.println(CLASS_NAME.concat("MEMBER LOGIN FAIL!!"));
            return null;

        }
        */

        Optional<MemberEntity> optionalMember =
                memberRepository.findByMemId(memberDto.getId());

        if (optionalMember.isPresent() &&
                passwordEncoder.matches(memberDto.getPw(), optionalMember.get().getMemPw())) {
            log.info("MEMBER LOGIN SUCCESS");
            return optionalMember.get().getMemId();

        } else {
            log.info("MEMBER LOGIN FAIL");
            return null;
        }

    }

    public MemberDto modify(String loginedID) {
        System.out.println(CLASS_NAME.concat("modify()"));

//        return memberDao.selectMemberByID(loginedID);
//        return memberMapper.selectMemberByID(loginedID);

        Optional<MemberEntity> optionalMember =
                memberRepository.findByMemId(loginedID);
        if (optionalMember.isPresent()) {
            MemberEntity memberEntity = optionalMember.get();
            /*
            MemberDto memberDto = MemberDto.builder()
                    .no(memberEntity.getMemNo())
                    .id(memberEntity.getMemId())
                    .mail(memberEntity.getMemMail())
                    .phone(memberEntity.getMemPhone())
                    .authority_no(memberEntity.getMemAuthorityNo())
                    .reg_date(memberEntity.getMemRegDate().toString())
                    .mod_date(memberEntity.getMemModDate().toString())
                    .build();

            return memberDto;
            */

            return memberEntity.toDto();

        }

        return null;
    }

    @Transactional
    public int modifyConfirm(MemberDto memberDto) {
        System.out.println(CLASS_NAME.concat("modifyConfirm()"));

        String encodedPW = passwordEncoder.encode(memberDto.getPw());
        memberDto.setPw(encodedPW);

//        return memberDao.updateMember(memberDto);
//        return memberMapper.updateMember(memberDto);

        Optional<MemberEntity> optionalMember =
                memberRepository.findById(memberDto.getNo());
        if (optionalMember.isPresent()) {
            MemberEntity memberEntity = optionalMember.get();
            memberEntity.setMemPw(memberDto.getPw());
            memberEntity.setMemMail(memberDto.getMail());
            memberEntity.setMemPhone(memberDto.getPhone());

//            memberRepository.save(memberEntity);

            return MODIFY_SUCCESS;

        } else {
            return MODIFY_FAIL;

        }
    }

    public int findpasswordConfirm(MemberDto memberDto) {
        System.out.println(CLASS_NAME.concat("findpasswordConfirm()"));

        // 1. 인증
//        MemberDto selectedMemberDto = memberDao.selectMemberByIDAndMail(memberDto);
        /*
        MemberDto selectedMemberDto = memberMapper.selectMemberByIDAndMail(memberDto);

        int result = 0;
        if (selectedMemberDto != null) {
            // 2. 새 비밀번호 생성
            String newPassword = createNewPassword();

            // 3. DB 업데이터
//            result = memberDao.updatePassword(memberDto.getId(), passwordEncoder.encode(newPassword));
            result = memberMapper.updatePassword(memberDto.getId(), passwordEncoder.encode(newPassword));

            if (result > 0) {
                // 4. 새 비밀번호 메일 발송
                sendNewPasswordByMail(memberDto.getMail(), newPassword);
            }
        }
        return result;
        */

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
        System.out.println("[MemberService] createNewPassword()");

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

        return stringBuffer.toString();

    }

    private void sendNewPasswordByMail(String toMailAddr, String newPassword) {
        System.out.println("[MemberService] sendNewPasswordByMail()");

        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setTo(toMailAddr);
        simpleMailMessage.setSubject("[MyCalendar] 새 비밀번호 안내입니다.");
        simpleMailMessage.setText("새 비밀번호: " + newPassword);
        simpleMailMessage.setFrom("hohasic@gmail.com");

        javaMailSender.send(simpleMailMessage);

    }

}
