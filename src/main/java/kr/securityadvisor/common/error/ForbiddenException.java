package kr.securityadvisor.common.error;

/**
 * 역할이 맞지 않거나 다른 기관의 자원에 접근할 때 던지는 예외. {@link GlobalExceptionHandler} 가 403 응답으로 바꾼다.
 *
 * <p>권한·기관 격리의 최종 판단은 서버가 한다(FUNCTIONAL_SPEC 3장). 클라이언트 role 값은 믿지 않는다.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
