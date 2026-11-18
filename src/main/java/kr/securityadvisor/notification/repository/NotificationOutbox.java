package kr.securityadvisor.notification.repository;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 업무와 같은 트랜잭션에 발송 의도를 보존한다. 본문/주소를 운영 지표에 노출하지 않는다. */
@Repository
@RequiredArgsConstructor
public class NotificationOutbox {
    private final JdbcTemplate jdbc;

    public record Entry(
            long id,
            String recipient,
            String eventKey,
            String channel,
            String kind,
            String title,
            String link,
            Long notificationId,
            int attempts,
            java.util.UUID claimToken) {}

    public void enqueue(
            String recipient,
            String eventKey,
            String channel,
            String kind,
            String title,
            String link,
            Long notificationId) {
        jdbc.update("""
            INSERT INTO notification_outbox(recipient,event_key,channel,kind,title,link,notification_id)
            VALUES (?,?,?,?,?,?,?) ON CONFLICT(recipient,event_key,channel) DO NOTHING
            """, recipient, eventKey, channel, kind, title, link, notificationId);
    }

    public List<Entry> claim() {
        return jdbc.query(
                """
            UPDATE notification_outbox SET status='RUNNING', attempts=attempts+1,
              available_at=now()+interval '5 minutes', updated_at=now(), claim_token=gen_random_uuid()
            WHERE notification_outbox_id IN (
              SELECT notification_outbox_id FROM notification_outbox
              WHERE status IN ('PENDING','RUNNING') AND available_at<=now() AND attempts<5
              ORDER BY notification_outbox_id LIMIT 20 FOR UPDATE SKIP LOCKED)
            RETURNING *
            """,
                (r, n) -> new Entry(
                        r.getLong("notification_outbox_id"),
                        r.getString("recipient"),
                        r.getString("event_key"),
                        r.getString("channel"),
                        r.getString("kind"),
                        r.getString("title"),
                        r.getString("link"),
                        r.getObject("notification_id", Long.class),
                        r.getInt("attempts"),
                        r.getObject("claim_token", java.util.UUID.class)));
    }

    public void finish(Entry e, String status) {
        jdbc.update(
                "UPDATE notification_outbox SET status=?, updated_at=now() WHERE notification_outbox_id=? AND claim_token=? AND status='RUNNING'",
                status,
                e.id(),
                e.claimToken());
    }

    public void failed(Entry e) {
        jdbc.update(
                """
            UPDATE notification_outbox SET status=?, available_at=now()+interval '1 minute'*?, updated_at=now()
            WHERE notification_outbox_id=? AND claim_token=? AND status='RUNNING'
            """, e.attempts() >= 5 ? "FAILED" : "PENDING", Math.min(30, 1 << e.attempts()), e.id(), e.claimToken());
    }

    public void expire() {
        jdbc.update(
                "UPDATE notification_outbox SET status='FAILED',updated_at=now() WHERE status='RUNNING' AND attempts>=5 AND available_at<=now()");
    }

    public List<Map<String, Object>> list(int page) {
        return jdbc.queryForList("""
            SELECT notification_outbox_id AS id,channel,status,attempts,created_at,updated_at
            FROM notification_outbox ORDER BY notification_outbox_id DESC LIMIT 50 OFFSET ?
            """, (long) page * 50);
    }

    public boolean retry(long id, String actor) {
        return jdbc.update("""
                        WITH changed AS (UPDATE notification_outbox SET status='PENDING',attempts=0,available_at=now(),updated_at=now(),claim_token=null
                         WHERE notification_outbox_id=? AND status='FAILED' RETURNING notification_outbox_id)
                        INSERT INTO notification_retry_records(notification_outbox_id,actor) SELECT notification_outbox_id,? FROM changed
                        """, id, actor) == 1;
    }
}
