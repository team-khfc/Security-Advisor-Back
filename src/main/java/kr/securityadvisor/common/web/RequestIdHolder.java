package kr.securityadvisor.common.web;

/** 현재 HTTP 요청의 식별자. 감사 이력과 ProblemDetail 의 requestId 로 쓴다. 요청 스레드에서만 유효하다. */
public final class RequestIdHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestIdHolder() {}

    public static String current() {
        return CURRENT.get();
    }

    static void set(String requestId) {
        CURRENT.set(requestId);
    }

    static void clear() {
        CURRENT.remove();
    }
}
