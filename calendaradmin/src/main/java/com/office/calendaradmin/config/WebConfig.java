package com.office.calendaradmin.config;

import com.office.calendaradmin.member.MemberSigninInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    MemberSigninInterceptor memberSigninInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(memberSigninInterceptor)
                .addPathPatterns(
                        "/member/modify"
                );

        /*
        registry.addInterceptor(memberSigninInterceptor)
                .addPathPatterns(
                        "/member/**"
                )
                .excludePathPatterns(
                        "/member/signup",
                        "/member/signup_confirm",
                        "/member/signin",
                        "/member/signin_confirm",
                        "/member/signout_confirm",
                        "/member/findpassword",
                        "/member/findpassword_confirm"
                );
        */
    }
}
