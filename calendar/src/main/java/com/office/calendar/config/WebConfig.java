package com.office.calendar.config;

import com.office.calendar.member.MemberSigninInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    MemberSigninInterceptor memberSigninInterceptor;

    // 업로드 이미지(c:/calendar/upload/{id}/{file}) -> /planUploadImg/{id}/{file}
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        registry.addResourceHandler("/planUploadImg/**")
                .addResourceLocations("file:///c:/calendar/upload/");

    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 로그인 체크는 Spring Security가 담당하므로 세션 기반 인터셉터는 사용하지 않음
        /*
        registry.addInterceptor(memberSigninInterceptor)
                .addPathPatterns(
                        "/member/modify"
                );
        */

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
