package kr.securityadvisor.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 현재 시각은 {@link Clock} 빈으로만 읽는다 — 기한·잠금 계산을 테스트에서 고정 시각으로 검증하기 위해서다. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
