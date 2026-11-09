package com.office.calendar.config;

import com.office.calendar.member.security.MemberAccessDeniedHandler;
import lombok.extern.slf4j.Slf4j;
import com.office.calendar.member.security.LoginLockFilter;
import com.office.calendar.member.security.LoginLockService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();

    }

    // loginLockService 는 로그인 시도 제한용이다. 보안 설정만 따로 올리는 테스트처럼 이 서비스가 없는 환경에서는 제한 없이 동작한다.
    @Bean SecurityFilterChain filterChain(HttpSecurity http, ObjectProvider<LoginLockService> loginLockProvider) throws Exception {

        LoginLockService loginLock = loginLockProvider.getIfAvailable();

        if (loginLock != null) {
            http.addFilterBefore(new LoginLockFilter(loginLock), UsernamePasswordAuthenticationFilter.class);
        }

        http
                .cors(cors -> cors.disable())
                .csrf(csrf -> csrf.disable());

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/css/*",
                                "/img/*",
                                "/js/*",
                                "/member/signup",
                                "/member/signup_confirm",
                                "/member/signin",
                                "/member/signin_confirm",
                                "/member/signin_result",
                                "/member/findpassword",
                                "/member/findpassword_confirm").permitAll()
                        .requestMatchers("/planner/**").hasAnyRole("USER")
                        .requestMatchers("/member/telegram/**", "/member/telegram", "/member/brief", "/member/brief/**").hasAnyRole("USER")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                );

        http
                .formLogin(login -> login
                        .loginPage("/member/signin")
                        .loginProcessingUrl("/member/signin_confirm")
                        .usernameParameter("id")
                        .passwordParameter("pw")
                        .successHandler((request, response, authentication) -> {
                            log.info("SIGNIN SUCCESS HANDLER()");

                            if (loginLock != null) {
                                loginLock.recordSuccess(request.getParameter("id"), request.getRemoteAddr());
                            }

                            User user = (User) authentication.getPrincipal();
                            String targetURI = "/member/signin_result?loginedID=" + user.getUsername();
                            response.sendRedirect(targetURI);

                        })
                        .failureHandler((request, response, exception) -> {
                            log.info("SIGNIN FAIL HANDLER()");

                            // 없는 아이디도 똑같이 기록해 존재 여부를 드러내지 않는다
                            if (loginLock != null) {
                                loginLock.recordFailure(request.getParameter("id"), request.getRemoteAddr());
                            }

                            String targetURI = "/member/signin_result";
                            response.sendRedirect(targetURI);

                        })
                );

        http
                .logout(logout -> logout
                        .logoutUrl("/member/signout_confirm")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            log.info("signout success handler");

                            String targetURI = "/";
                            response.sendRedirect(targetURI);

                        }));

        http
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler(new MemberAccessDeniedHandler())
                );

        return http.build();

    }

}
