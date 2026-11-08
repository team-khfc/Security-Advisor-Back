package kr.securityadvisor.common.error;

/** 외부 의존(LLM 등)을 지금 쓸 수 없음(503). 업무 상태는 바꾸지 않는다. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
