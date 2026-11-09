package com.office.calendar.member.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 로그인 처리(POST /member/signin_confirm) 앞에서 (아이디, IP)가 잠겨 있으면 인증을 시도하지 않고 바로 거부한다.
// 그래서 올바른 비밀번호여도 거부되고, 잠금 중의 시도는 실패 횟수를 더 올리지 않는다.
// 없는 아이디도 같은 방식으로 판단하므로 응답으로 아이디 존재 여부를 알 수 없다.
@Slf4j
@RequiredArgsConstructor
public class LoginLockFilter extends OncePerRequestFilter {

    public static final String SIGNIN_URL = "/member/signin_confirm";
    public static final String LOCKED_REDIRECT = "/member/signin_result?locked=true";

    private final LoginLockService loginLockService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && (request.getContextPath() + SIGNIN_URL).equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (loginLockService.isLocked(request.getParameter("id"), request.getRemoteAddr())) {
            log.info("login rejected: locked");
            response.sendRedirect(request.getContextPath() + LOCKED_REDIRECT);
            return;
        }

        chain.doFilter(request, response);
    }

}
