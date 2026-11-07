package kr.securityadvisor.common.config;

import java.util.Optional;
import kr.securityadvisor.common.security.MemberPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JPA 공통 설정.
 *
 * <p>{@link kr.securityadvisor.common.entity.BaseTimeEntity} 의 생성·수정 시각과
 * {@link kr.securityadvisor.common.entity.BaseAuditEntity} 의 생성·수정자를 자동으로 채우기 위해 Auditing 을 켠다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {

    /** 로그인한 사용자의 username 을 생성·수정자로 기록한다. 로그인 정보가 없으면(시드·배치) 비워 둔다. */
    @Bean
    AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof MemberPrincipal principal)) {
                return Optional.empty();
            }
            return Optional.of(principal.username());
        };
    }
}
