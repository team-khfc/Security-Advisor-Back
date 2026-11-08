package kr.securityadvisor.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** 비동기 작업(F-02 문서 요약). 실행기는 Boot 기본 TaskExecutor. */
@Configuration
@EnableAsync
public class AsyncConfig {}
