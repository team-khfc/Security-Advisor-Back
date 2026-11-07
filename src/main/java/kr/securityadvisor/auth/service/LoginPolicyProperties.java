package kr.securityadvisor.auth.service;

import java.time.Duration;
import kr.securityadvisor.auth.domain.LoginLockPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 실패 제한 설정(application.yml {@code securityadvisor.auth.*}).
 *
 * @param maxFailedLogins 연속 실패 허용 횟수(이 횟수에 도달하면 잠금)
 * @param lockDuration 잠금 유지 시간
 */
@ConfigurationProperties(prefix = "securityadvisor.auth")
public record LoginPolicyProperties(int maxFailedLogins, Duration lockDuration) {

    public LoginLockPolicy toPolicy() {
        return new LoginLockPolicy(maxFailedLogins, lockDuration);
    }
}
