package kr.securityadvisor.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 예약 작업(F-13 기한 알림). 스케줄 표현식은 각 작업의 설정 키로 조정한다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
