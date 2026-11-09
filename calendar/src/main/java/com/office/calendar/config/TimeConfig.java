package com.office.calendar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

// 시각을 쓰는 서비스(연동 코드 만료, 브리핑 주 계산)가 시계를 주입받아 테스트에서 시각을 고정할 수 있게 한다.
@Configuration
public class TimeConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);

    }

}
