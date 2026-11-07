package kr.securityadvisor.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시연 계정 설정. 비밀번호는 환경변수 {@code DEMO_ACCOUNT_PASSWORD} 로만 받는다(코드·마이그레이션 평문 금지).
 *
 * @param accountPassword 시연 계정 공통 비밀번호. 비어 있으면 시연 계정을 만들지 않는다
 */
@ConfigurationProperties(prefix = "securityadvisor.demo")
public record DemoAccountProperties(String accountPassword) {

    public boolean hasPassword() {
        return accountPassword != null && !accountPassword.isBlank();
    }
}
