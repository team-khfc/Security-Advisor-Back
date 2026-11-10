package kr.securityadvisor.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.securityadvisor.common.error.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** 계정 변경 전 세션을 업무 처리·CSRF 검사보다 먼저 차단하고 재로그인을 요구한다. */
@RequiredArgsConstructor
public class AccountSessionFilter extends OncePerRequestFilter {
    private final AccountSessionValidator validator;
    private final HandlerExceptionResolver exceptionResolver;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof MemberPrincipal principal
                && !validator.isValid(principal)) {
            logoutHandler.logout(request, response, authentication);
            exceptionResolver.resolveException(
                    request, response, null, new UnauthorizedException("계정 권한이 변경되었거나 비활성화되었습니다. 다시 로그인해 주세요"));
            return;
        }
        chain.doFilter(request, response);
    }
}
