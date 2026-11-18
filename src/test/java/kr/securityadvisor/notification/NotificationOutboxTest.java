package kr.securityadvisor.notification;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.notification.repository.NotificationOutbox;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("docker")
@SpringBootTest(
        properties = {
            "securityadvisor.demo.account-password=Test-Demo-1234",
            "securityadvisor.notification.retry-delay-ms=3600000"
        })
@Import(TestcontainersConfiguration.class)
class NotificationOutboxTest {
    @Autowired
    NotificationOutbox outbox;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Test
    void 업무롤백_중복_실패재시도_임대복구를_검사한다() {
        String key = "synthetic:" + UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            outbox.enqueue("synthetic", key, "SCREEN", "ASSIGNED", "합성", "/requests/1", null);
            s.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where event_key=?", Long.class, key))
                .isZero();
        outbox.enqueue("synthetic", key, "SCREEN", "ASSIGNED", "합성", "/requests/1", null);
        outbox.enqueue("synthetic", key, "SCREEN", "ASSIGNED", "합성", "/requests/1", null);
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where event_key=?", Long.class, key))
                .isEqualTo(1);
        var first = outbox.claim().stream()
                .filter(e -> e.eventKey().equals(key))
                .findFirst()
                .orElseThrow();
        assertThat(outbox.claim()).noneMatch(e -> e.id() == first.id());
        outbox.failed(first);
        jdbc.update(
                "update notification_outbox set available_at=now()-interval '1 second' where notification_outbox_id=?",
                first.id());
        var next = outbox.claim().stream()
                .filter(e -> e.id() == first.id())
                .findFirst()
                .orElseThrow();
        assertThat(next.attempts()).isEqualTo(2);
        outbox.finish(first, "SENT");
        assertThat(jdbc.queryForObject(
                        "select status from notification_outbox where notification_outbox_id=?",
                        String.class,
                        first.id()))
                .isEqualTo("RUNNING");
        outbox.finish(next, "SENT");
        assertThat(outbox.retry(next.id(), "synthetic-admin")).isFalse();
        jdbc.update("update notification_outbox set status='FAILED' where notification_outbox_id=?", next.id());
        assertThat(outbox.retry(next.id(), "synthetic-admin")).isTrue();
        var retried = outbox.claim().stream()
                .filter(e -> e.id() == next.id())
                .findFirst()
                .orElseThrow();
        assertThat(retried.attempts()).isEqualTo(1);
        assertThat(retried.claimToken()).isNotEqualTo(first.claimToken());
        outbox.finish(first, "SENT");
        assertThat(jdbc.queryForObject(
                        "select status from notification_outbox where notification_outbox_id=?",
                        String.class,
                        next.id()))
                .isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject(
                        "select actor from notification_retry_records where notification_outbox_id=?",
                        String.class,
                        next.id()))
                .isEqualTo("synthetic-admin");
        outbox.finish(retried, "SENT");
    }
}
