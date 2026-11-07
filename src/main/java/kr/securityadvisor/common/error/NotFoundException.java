package kr.securityadvisor.common.error;

/**
 * 요청한 리소스가 없을 때 던지는 예외. {@link GlobalExceptionHandler} 가 404 응답으로 바꾼다.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
