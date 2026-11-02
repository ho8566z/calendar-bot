package com.office.calendaradmin.config;

import com.office.calendaradmin.member.jpa.AuthorityEntity;
import com.office.calendaradmin.member.jpa.AuthorityRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initDatabase(AuthorityRepository authorityRepository) {

        return args -> {

            if (!authorityRepository.existsByAuthRoleName("PRE_ADMIN")) {
                authorityRepository.save(
                        AuthorityEntity.builder()
                                .authRoleName("PRE_ADMIN")
                                .build()
                );
            }

            if (!authorityRepository.existsByAuthRoleName("ADMIN")) {
                authorityRepository.save(
                        AuthorityEntity.builder()
                                .authRoleName("ADMIN")
                                .build()
                );
            }

            if (!authorityRepository.existsByAuthRoleName("SUPER_ADMIN")) {
                authorityRepository.save(
                        AuthorityEntity.builder()
                                .authRoleName("SUPER_ADMIN")
                                .build()
                );
            }

        };

    }

}
