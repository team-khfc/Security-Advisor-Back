package kr.securityadvisor.common.error;

/**
 * 도메인 불변식(FUNCTIONAL_SPEC 5장)을 어기는 상태 전이를 시도할 때 domain 계층이 던지는 예외.
 * {@link GlobalExceptionHandler} 가 422 응답으로 바꾼다.
 *
 * <p>domain 계층은 프레임워크를 모르므로 이 예외는 순수 Java 예외다. 상태 전이는 setter 가 아니라
 * 도메인 메서드로만 하고, 조건을 어기면 이 예외를 던진다(불변식 10).
 */
public class DomainRuleViolationException extends RuntimeException {

    public DomainRuleViolationException(String message) {
        super(message);
    }
}
