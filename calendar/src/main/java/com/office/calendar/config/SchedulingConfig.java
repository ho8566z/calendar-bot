package com.office.calendar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 텔레그램 폴링(롱 폴링)과 주간 브리핑 발송처럼 정해진 시각·간격으로 도는 작업을 켠다.
// 스레드 풀 크기는 application.properties의 spring.task.scheduling.pool.size 로 정한다
// (롱 폴링이 한 스레드를 오래 잡아도 브리핑 발송이 밀리지 않게 2개 이상으로 둔다).
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
