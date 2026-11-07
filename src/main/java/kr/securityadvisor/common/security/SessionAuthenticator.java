package kr.securityadvisor.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 시연용 세션 로그인의 HTTP 세션 처리. 인증 성공 후 SecurityContext 를 세션에 저장하고, 로그아웃 시 세션을 무효화한다.
 *
 * <p>자격 증명 검증(아이디·비밀번호 대조)은 auth 컨텍스트의 서비스가 하고, 이 클래스는 세션 저장만 맡는다.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticator {

    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    /** 검증이 끝난 사용자를 현재 요청의 세션에 로그인 상태로 저장한다. */
    public void login(MemberPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(principal.role().authority())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /** 세션을 무효화하고 SecurityContext 를 비운다. 로그인하지 않은 상태에서 호출해도 오류가 아니다. */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logoutHandler.logout(request, response, authentication);
    }
}
