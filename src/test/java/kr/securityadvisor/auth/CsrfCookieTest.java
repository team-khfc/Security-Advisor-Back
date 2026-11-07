package kr.securityadvisor.auth;

import static org.assertj.core.api.Assertions.assertThat;

import kr.securityadvisor.auth.controller.AuthController;
import kr.securityadvisor.auth.service.AuthService;
import kr.securityadvisor.common.config.SecurityConfig;
import kr.securityadvisor.common.security.SessionAuthenticator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * XSRF-TOKEN 쿠키 발급은 별도 컨텍스트에서 검사한다.
 *
 * <p>이유: {@code SecurityMockMvcRequestPostProcessors.csrf()} 는 애플리케이션 {@code CsrfFilter} 의 저장소를
 * {@code TestCsrfTokenRepository(HttpSessionCsrfTokenRepository)} 로 바꿔치기한다(싱글턴 필터를 직접 수정). 같은 컨텍스트에서
 * {@code csrf()} 를 한 번이라도 쓰면 이후 요청은 쿠키 저장소를 쓰지 않아 쿠키가 나오지 않는다. 그래서 이 클래스는 {@code csrf()} 를
 * 쓰지 않고, 고유한 속성으로 컨텍스트 캐시도 분리한다.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "securityadvisor.test.context=csrf-cookie")
class CsrfCookieTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private SessionAuthenticator sessionAuthenticator;

    @Test
    @DisplayName("GET 응답에는 XSRF-TOKEN 쿠키가 실린다 (프론트가 먼저 받아 X-XSRF-TOKEN 헤더로 보낸다)")
    void csrfCookieIssuedOnGet() {
        assertThat(mvc.get().uri("/api/auth/me"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .cookies()
                .containsCookie("XSRF-TOKEN");
    }
}
