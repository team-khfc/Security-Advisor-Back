package kr.securityadvisor.auth.controller;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.securityadvisor.auth.dto.LoginRequest;
import kr.securityadvisor.auth.dto.MemberResponse;
import kr.securityadvisor.auth.dto.MemberResponse.LoginResult;
import kr.securityadvisor.auth.service.AuthService;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.SessionAuthenticator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시연용 인증 API. 세션 쿠키 기반이며 실제 SSO 연동은 범위 밖(P3)이다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionAuthenticator sessionAuthenticator;

    /** 로그인. 성공하면 세션을 만들고 사용자 정보를 돌려준다. 실패는 401(ProblemDetail). */
    @PostMapping("/login")
    public MemberResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        LoginResult result = authService.login(request);
        sessionAuthenticator.login(result.principal(), httpRequest, httpResponse);
        return result.member();
    }

    /** 로그아웃. 세션을 무효화한다. 로그인하지 않은 상태여도 204. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        sessionAuthenticator.logout(httpRequest, httpResponse);
    }

    /** 현재 로그인한 사용자. 세션이 없거나 만료되면 401. */
    @GetMapping("/me")
    public MemberResponse me(@CurrentMember MemberPrincipal member) {
        return authService.getCurrentMember(member);
    }
}
