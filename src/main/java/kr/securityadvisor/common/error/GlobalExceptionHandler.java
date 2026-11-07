package kr.securityadvisor.common.error;

import kr.securityadvisor.common.web.RequestIdHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
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

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "요청이 올바르지 않습니다", e.getMessage());
    }

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

    /** CSRF 토큰 누락·불일치. {@code AccessDeniedException} 의 하위 타입이라 더 구체적인 이 핸들러가 먼저 잡는다. */
    @ExceptionHandler(CsrfException.class)
    public ProblemDetail handleCsrf(CsrfException e) {
        return problem(
                HttpStatus.FORBIDDEN, "권한이 없습니다", "CSRF 토큰이 없거나 유효하지 않습니다. XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내세요");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "권한이 없습니다", "이 작업을 수행할 권한이 없습니다");
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ProblemDetail handleTooManyRequests(TooManyRequestsException e) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "요청이 제한되었습니다", e.getMessage());
    }

    /** 유일 제약 위반 등 DB 무결성 충돌(동시 가입 경쟁 등). 내부 메시지는 노출하지 않는다. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "충돌이 발생했습니다", "이미 존재하는 값이거나 데이터 제약에 어긋납니다");
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
        String requestId = RequestIdHolder.current();
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }
}
