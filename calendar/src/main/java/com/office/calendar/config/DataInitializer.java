package com.office.calendar.config;

import com.office.calendar.admin.AdminSeedService;
import com.office.calendar.member.jpa.AuthorityEntity;
import com.office.calendar.member.jpa.AuthorityRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initDatabase(AuthorityRepository authorityRepository,
                                          AdminSeedService adminSeedService) {

        return args -> {

            if (!authorityRepository.existsByAuthRoleName("PRE_USER")) {
                authorityRepository.save(
                        AuthorityEntity.builder()
                                .authRoleName("PRE_USER")
                                .build()
                );
            }

            if (!authorityRepository.existsByAuthRoleName("USER")) {
                authorityRepository.save(
                        AuthorityEntity.builder()
                                .authRoleName("USER")
                                .build()
                );
            }

            if (!authorityRepository.existsByAuthRoleName(AdminSeedService.ROLE_ADMIN)) {
                authorityRepository.save(
                        AuthorityEntity.builder()
                                .authRoleName(AdminSeedService.ROLE_ADMIN)
                                .build()
                );
            }

            // 관리자 계정이 없으면 환경변수 값으로 1개 만든다
            adminSeedService.seedIfAbsent();

        };

    }

}
