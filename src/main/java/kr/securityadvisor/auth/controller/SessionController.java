package kr.securityadvisor.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 세션 자원. 세션은 요청마다 비활동 시간이 다시 시작되므로 "연장"은 세션을 한 번 건드리고 새 만료 예정 시각을 돌려주는 것이다.
 * 로그인이 없으면 401(보안 필터).
 */
@RestController
public class SessionController {

    @PostMapping("/api/sessions/current/renewals")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Instant> renew(@CurrentMember MemberPrincipal member, HttpServletRequest request) {
        Instant expiresAt = AuthController.expiresAt(request);
        return Map.of("expiresAt", expiresAt == null ? Instant.now() : expiresAt);
    }
}
