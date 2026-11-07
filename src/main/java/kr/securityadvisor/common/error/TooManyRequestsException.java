package kr.securityadvisor.common.error;

/**
 * 로그인 시도 제한을 넘겼을 때 던지는 예외. {@link GlobalExceptionHandler} 가 429 응답으로 바꾼다(AGENTS.md 5장 상태 코드 표).
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
