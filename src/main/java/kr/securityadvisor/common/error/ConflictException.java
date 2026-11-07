package kr.securityadvisor.common.error;

/**
 * 오래된 revision 으로 쓰기를 시도했거나(낙관적 잠금) 멱등성 키가 중복될 때 던지는 예외.
 * {@link GlobalExceptionHandler} 가 409 응답으로 바꾼다. 클라이언트는 미저장 값을 보존한 채 최신 상태를 다시 읽어야 한다.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
