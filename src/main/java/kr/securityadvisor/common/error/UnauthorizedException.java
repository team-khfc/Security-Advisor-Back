package kr.securityadvisor.common.error;

/**
 * 로그인하지 않았거나 자격 증명이 틀렸을 때 던지는 예외. {@link GlobalExceptionHandler} 가 401 응답으로 바꾼다.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
