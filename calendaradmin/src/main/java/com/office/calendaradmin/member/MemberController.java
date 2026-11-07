package com.office.calendaradmin.member;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/member")
public class MemberController {

    final private MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 회원가입 양식
    @GetMapping("/signup")
    public String signup() {
        log.info("signup()");

        String nextPage = "member/signup_form";

        return nextPage;

    }

    // 회원가입 확인
    @PostMapping("/signup_confirm")
    public String signupConfirm(MemberDto memberDto, Model model) {
        log.info("signupConfirm()");

        String nextPage = "member/signup_result";

        int result = memberService.signupConfirm(memberDto);
        model.addAttribute("result", result);

        return nextPage;

    }

    // 로그인 양식 /signin
    @GetMapping("/signin")
    public String signin() {
        log.info("signin()");

        String nextPage = "member/signin_form";

        return nextPage;

    }

    // 비밀번호 찾기 양식(/member/findpassword)
    @GetMapping("/findpassword")
    public String findpassword(MemberDto memberDto, Model model) {
        log.info("findpassword()");

        String nextPage = "member/findpassword_form";

        return nextPage;

    }

    // 비밀번호 찾기 확인(/member/findpassword_confirm)
    @PostMapping("/findpassword_confirm")
    public String findpasswordConfirm(MemberDto memberDto, Model model) {
        log.info("findpasswordConfirm()");

        String nextPage = "member/findpassword_result";

        int result = memberService.findpasswordConfirm(memberDto);
        model.addAttribute("result", result);

        return nextPage;

    }

    //
    @GetMapping("/signin_result")
    public String signinResult(
            @RequestParam(value = "loginedID", required = false) String loginedID,
            Model model) {
        log.info("signinResult()");

        String nextPage = "member/signin_result";
        model.addAttribute("loginedID", loginedID);

        return nextPage;
    }

}
