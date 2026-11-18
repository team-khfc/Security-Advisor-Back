package kr.securityadvisor.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import kr.securityadvisor.common.error.*;
import kr.securityadvisor.common.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 민감한 태그 없이 분 단위 누적 지표를 30일 보존하고 장애 발생·해소 이력을 남긴다. */
@Service
@RequiredArgsConstructor
public class OperationHistory {
    private final MeterRegistry registry;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String instance = UUID.randomUUID().toString();
    private Map<String, Double> previous = Map.of();

    @Scheduled(fixedDelayString = "${securityadvisor.operations.sample-delay-ms:60000}", initialDelay = 30000)
    public synchronized void sample() {
        Map<String, Double> values = new TreeMap<>();
        for (var timer : registry.find("http.server.requests").timers()) {
            values.merge("http.count", (double) timer.count(), Double::sum);
            values.merge("http.seconds", timer.totalTime(java.util.concurrent.TimeUnit.SECONDS), Double::sum);
            String status = timer.getId().getTag("status");
            if (status != null && status.startsWith("5"))
                values.merge("http.errors", (double) timer.count(), Double::sum);
        }
        for (String category : List.of("llm", "notification", "reminder")) {
            for (var timer :
                    registry.find("securityadvisor." + category + ".executions").timers()) {
                values.merge(category + ".count", (double) timer.count(), Double::sum);
                values.merge(
                        category + ".seconds", timer.totalTime(java.util.concurrent.TimeUnit.SECONDS), Double::sum);
                if (Set.of("failure", "timeout").contains(timer.getId().getTag("outcome")))
                    values.merge(category + ".errors", (double) timer.count(), Double::sum);
            }
        }
        jdbc.update(
                "INSERT INTO operation_samples(instance_id,metrics) VALUES (?,?)",
                instance,
                json.writeValueAsString(values));
        if (!previous.isEmpty())
            for (String category : List.of("http", "llm", "notification", "reminder")) {
                double count =
                        values.getOrDefault(category + ".count", 0d) - previous.getOrDefault(category + ".count", 0d);
                double errors =
                        values.getOrDefault(category + ".errors", 0d) - previous.getOrDefault(category + ".errors", 0d);
                alert(
                        category.toUpperCase(Locale.ROOT) + "_ERRORS",
                        errors >= 3 && count > 0 && errors / count >= 0.25,
                        category + " 호출 오류가 최근 수집 구간에서 3건 이상·25% 이상 발생했습니다.");
            }
        previous = Map.copyOf(values);
        alert(
                "ANALYSIS_STALLED",
                count(
                                "SELECT count(*) FROM analysis_jobs WHERE status IN ('QUEUED','RUNNING') AND created_at < now()-interval '20 minutes'")
                        > 0,
                "20분 이상 완료되지 않은 문서 분석 작업이 있습니다.");
        alert(
                "NOTIFICATION_FAILED",
                count("SELECT count(*) FROM notification_outbox WHERE status='FAILED'") > 0,
                "자동 재시도가 끝난 알림이 있습니다. 발송 기록을 확인해 주세요.");
        jdbc.update("DELETE FROM operation_samples WHERE recorded_at<now()-interval '30 days'");
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private void alert(String code, boolean active, String message) {
        if (active)
            jdbc.update(
                    "INSERT INTO operation_alerts(code,message) VALUES (?,?) ON CONFLICT(code) WHERE resolved_at IS NULL DO NOTHING",
                    code,
                    message);
        else jdbc.update("UPDATE operation_alerts SET resolved_at=now() WHERE code=? AND resolved_at IS NULL", code);
    }

    public record History(List<Map<String, Object>> samples, List<Map<String, Object>> alerts, int retentionDays) {}

    public History history(MemberPrincipal actor, int page) {
        actor.requireAnyRole(MemberRole.ADMIN);
        if (page < 0 || page > 10000) throw new BadRequestException("페이지 범위가 올바르지 않습니다");
        return new History(
                jdbc.queryForList(
                        "SELECT * FROM operation_samples ORDER BY operation_sample_id DESC LIMIT 60 OFFSET ?",
                        (long) page * 60),
                jdbc.queryForList("SELECT * FROM operation_alerts ORDER BY operation_alert_id DESC LIMIT 100"),
                30);
    }

    public void acknowledge(MemberPrincipal actor, long id) {
        actor.requireAnyRole(MemberRole.ADMIN);
        if (jdbc.update(
                        "UPDATE operation_alerts SET acknowledged_by=?,acknowledged_at=now() WHERE operation_alert_id=? AND acknowledged_at IS NULL",
                        actor.username(),
                        id)
                != 1) throw new ConflictException("이미 확인했거나 존재하지 않는 경보입니다");
    }
}
