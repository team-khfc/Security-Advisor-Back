package kr.securityadvisor.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * SPA(Next.js)용 CSRF 토큰 처리 — Spring Security 참조 문서의 권장 구성.
 *
 * <p>토큰은 {@code XSRF-TOKEN} 쿠키(HttpOnly 아님)로 내려가고, 프론트는 그 값을 상태 변경 요청의
 * {@code X-XSRF-TOKEN} 헤더에 실어 보낸다. 헤더로 온 값은 그대로 비교하고(쿠키 값은 XOR 인코딩 전이므로),
 * 폼 파라미터로 온 값은 BREACH 대응을 위해 XOR 인코딩된 값으로 본다.
 * {@link #handle} 에서 토큰을 한 번 읽어 매 응답에 쿠키가 실리도록 한다(별도 필터 불필요).
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        // 지연 로딩된 토큰을 읽어야 CookieCsrfTokenRepository 가 쿠키를 응답에 쓴다.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(headerValue) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
