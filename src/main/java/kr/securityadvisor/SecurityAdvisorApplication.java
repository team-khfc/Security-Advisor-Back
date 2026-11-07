package kr.securityadvisor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 보안 서류 자동 생성 시스템 백엔드의 진입점.
 */
@SpringBootApplication
public class SecurityAdvisorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityAdvisorApplication.class, args);
    }
}
