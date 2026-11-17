package kr.securityadvisor.common.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** 원문·식별자·예외 메시지 없이 한정된 분류만 수집한다. 지표 실패는 업무 결과를 바꾸지 않는다. */
@Component
public class OperationMetrics {
    public enum Operation {
        LLM,
        REMINDER,
        NOTIFICATION
    }

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicLong> successes = new ConcurrentHashMap<>();

    public OperationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 성공·실패·원인 체인의 시간 초과를 구분하고 원래 반환값/예외를 그대로 전달한다. */
    public <T> T measure(Operation operation, String category, Supplier<T> work) {
        long started = System.nanoTime();
        String outcome = "success";
        try {
            T result = work.get();
            if (operation == Operation.NOTIFICATION && "in_app".equals(category) && result == null) outcome = "skipped";
            return result;
        } catch (RuntimeException e) {
            outcome = timeout(e) ? "timeout" : "failure";
            throw e;
        } finally {
            record(operation, category, outcome, System.nanoTime() - started);
        }
    }

    private void record(Operation operation, String category, String outcome, long nanos) {
        String safe = switch (operation) {
            case LLM -> Set.of("INTERVIEW", "SUMMARY", "DOCUMENT", "INQUIRY").contains(category) ? category : "OTHER";
            case REMINDER -> Set.of("scheduled", "manual").contains(category) ? category : "other";
            case NOTIFICATION -> Set.of("in_app", "EMAIL", "WEBHOOK").contains(category) ? category : "external";
        };
        String name = "securityadvisor." + operation.name().toLowerCase(java.util.Locale.ROOT);
        try {
            Timer.builder(name + ".executions")
                    .tags("category", safe, "outcome", outcome)
                    .description("업무 호출 횟수와 소요 시간; success는 호출 완료이며 외부 수신 확인이 아님")
                    .register(registry)
                    .record(nanos, TimeUnit.NANOSECONDS);
            if (operation == Operation.REMINDER && outcome.equals("success")) {
                successes
                        .computeIfAbsent(safe, key -> {
                            AtomicLong value = new AtomicLong();
                            Gauge.builder(name + ".last.success", value, AtomicLong::doubleValue)
                                    .tag("category", key)
                                    .description("마지막 정상 실행의 Unix 초; 재기동 시 초기화")
                                    .register(registry);
                            return value;
                        })
                        .set(java.time.Instant.now().getEpochSecond());
            }
        } catch (RuntimeException ignored) {
            // 관측 장애로 AI 응답·알림·업무 트랜잭션의 원래 결과를 잃지 않는다.
        }
    }

    private static boolean timeout(Throwable error) {
        Set<Throwable> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable cause = error; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) return true;
        }
        return false;
    }
}
