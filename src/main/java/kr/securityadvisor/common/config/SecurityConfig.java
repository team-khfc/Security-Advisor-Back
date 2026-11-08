package kr.securityadvisor.common.config;

import kr.securityadvisor.common.security.SpaCsrfTokenRequestHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 시연용 인증 설정(FUNCTIONAL_SPEC F-11). 운영 기관은 설치당 1곳이라 기관 격리 검사는 없다.
 *
 * <ul>
 *   <li>세션 기반 로그인: {@code POST /api/auth/login}·{@code /logout}·{@code POST /api/members}(회원가입)만 익명 허용,
 *       나머지 {@code /api/**} 는 로그인 필수.</li>
 *   <li>CSRF: 상태 변경 요청은 {@code XSRF-TOKEN} 쿠키 ↔ {@code X-XSRF-TOKEN} 헤더로 검사한다(SECURE_CODING.md 1절).
 *       로그인·회원가입도 포함한다(로그인 CSRF 방지). 프론트는 먼저 아무 GET(예: {@code /api/auth/me})을 호출해 쿠키를 받는다.</li>
 *   <li>인증·인가 실패는 {@code GlobalExceptionHandler} 로 넘겨 ProblemDetail(401/403)로 응답한다.</li>
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
        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout", "/api/members")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/api/installation",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs.yaml",
                                "/v3/api-docs/**")
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
