package kr.securityadvisor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 보안길잡이(SecurityAdvisor) 백엔드의 진입점 — 현업과 보안·인프라 담당자 사이의 보안 서류 작성·협의를 중계한다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SecurityAdvisorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityAdvisorApplication.class, args);
    }
}
