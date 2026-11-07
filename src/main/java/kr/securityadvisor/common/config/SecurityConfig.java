package kr.securityadvisor.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 시연용 인증 설정(FUNCTIONAL_SPEC F-11). 단일 기관 운영이므로 기관 격리 검사는 없다(ADR-0003).
 *
 * <ul>
 *   <li>세션 기반 로그인: {@code POST /api/auth/login} 만 익명 허용, 나머지 {@code /api/**} 는 로그인 필수.</li>
 *   <li>인증·인가 실패는 {@code GlobalExceptionHandler} 로 넘겨 ProblemDetail(401/403)로 응답한다.</li>
 *   <li>CSRF 는 끈다 — JSON API 만 제공하고 쿠키는 SameSite 기본값(Lax)에 의존한다. 실제 SSO 도입(P3) 시 재검토한다.</li>
 *   <li>역할 검사는 {@code @PreAuthorize("hasRole('ADMIN')")} 또는 서비스의 {@code MemberPrincipal} 검사로 한다.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login", "/api/auth/logout")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling
                        // 401/403 도 다른 오류와 같은 ProblemDetail 형식으로 내보내기 위해 MVC 예외 처리로 위임한다.
                        .authenticationEntryPoint((request, response, exception) ->
                                exceptionResolver.resolveException(request, response, null, exception))
                        .accessDeniedHandler((request, response, exception) ->
                                exceptionResolver.resolveException(request, response, null, exception)));
        return http.build();
    }

    /** 로그인 상태를 HTTP 세션에 보관한다. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** 비밀번호 해시. 저장 형식은 {@code {bcrypt}$2b$...} 처럼 알고리즘 접두사를 포함한다. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
