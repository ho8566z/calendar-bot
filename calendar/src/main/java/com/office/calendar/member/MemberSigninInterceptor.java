package com.office.calendar.member;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class MemberSigninInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.debug("preHandle()");

        HttpSession session = request.getSession();
        Object obj = session.getAttribute("loginedID");
        if (obj != null)
            return true;

        response.sendRedirect("/member/signin");
        return false;

    }
}
