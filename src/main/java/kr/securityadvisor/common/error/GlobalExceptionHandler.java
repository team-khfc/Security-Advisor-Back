package kr.securityadvisor.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 API 오류를 RFC 9457 ProblemDetail 형식(JSON)으로 통일한다.
 *
 * <p>검증 실패(400) 등 스프링 기본 예외는 부모 클래스가 처리하고, 이 프로젝트의 예외만 여기에 추가한다.
 * 새 예외를 만들면 이 클래스에 핸들러를 추가한다. Spring Security 의 인증·인가 예외도
 * {@code SecurityConfig} 가 이 핸들러로 넘겨 같은 형식으로 응답한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다", e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException e) {
        return problem(HttpStatus.UNAUTHORIZED, "인증이 필요합니다", e.getMessage());
    }

    /** 로그인하지 않았거나 세션이 만료된 요청. 화면은 이 응답을 받으면 세션 만료 안내를 띄운다. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException e) {
        return problem(HttpStatus.UNAUTHORIZED, "인증이 필요합니다", "로그인이 필요하거나 세션이 만료되었습니다");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, "권한이 없습니다", e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "권한이 없습니다", "이 작업을 수행할 권한이 없습니다");
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "충돌이 발생했습니다", e.getMessage());
    }

    @ExceptionHandler(DomainRuleViolationException.class)
    public ProblemDetail handleDomainRule(DomainRuleViolationException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "업무 규칙에 어긋납니다", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
