package com.office.calendar.config;

import com.office.calendar.member.jpa.AuthorityEntity;
import com.office.calendar.member.jpa.AuthorityRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initDatabase(AuthorityRepository authorityRepository) {

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

        };

    }

}
