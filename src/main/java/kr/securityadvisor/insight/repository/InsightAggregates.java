package kr.securityadvisor.insight.repository;

import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 감사 이력은 DB에서 최초 시각·연속 행위를 집계해 원문 전체 로딩을 피한다. */
@Repository
@RequiredArgsConstructor
public class InsightAggregates {
    private final JdbcTemplate jdbc;

    public record RequestCount(String docType, String status, int stage, long count) {}

    public List<RequestCount> requests() {
        return jdbc.query(
                "SELECT doc_type,status,stage,count(*) FROM security_requests GROUP BY doc_type,status,stage",
                (r, n) -> new RequestCount(r.getString(1), r.getString(2), r.getInt(3), r.getLong(4)));
    }

    public record RequestPolicy(long requestId, long publicationId) {}

    public List<RequestPolicy> policies() {
        return jdbc.query(
                "SELECT r.security_request_id,b.publication_id FROM security_requests r JOIN business_cases b USING(business_case_id)",
                (r, n) -> new RequestPolicy(r.getLong(1), r.getLong(2)));
    }

    public record Stage(String target, String action, Instant at) {}

    public List<Stage> stages() {
        return jdbc.query(
                """
            SELECT target_id,action,min(occurred_at) AS at FROM audit_events
            WHERE target_type='SECURITY_REQUEST' AND action IN
            ('SECURITY_REQUEST_CREATED','REVIEW_REQUESTED','STAGE_ADVANCED','SUBMITTED','REQUEST_CONFIRMED','SUPPLEMENT_REQUESTED','STAGE_RETURNED')
            GROUP BY target_id,action
            """,
                (r, n) -> new Stage(
                        r.getString(1), r.getString(2), r.getTimestamp(3).toInstant()));
    }

    public long[] ai() {
        return jdbc.queryForObject("""
            WITH actions AS (
              SELECT action,lag(action) OVER(PARTITION BY target_type,target_id,split_part(coalesce(summary,''),' ',1)
              ORDER BY occurred_at,audit_event_id) AS previous FROM audit_events
              WHERE action IN ('ANSWER_AI_PROPOSED','ANSWER_CONFIRMED','ANSWER_STAFF_CONFIRMED','ANSWER_UPDATED','ANSWER_STAFF_INPUT'))
            SELECT count(*) FILTER(WHERE action='ANSWER_AI_PROPOSED'),
              count(*) FILTER(WHERE previous='ANSWER_AI_PROPOSED' AND action IN ('ANSWER_CONFIRMED','ANSWER_STAFF_CONFIRMED')),
              count(*) FILTER(WHERE previous='ANSWER_AI_PROPOSED' AND action IN ('ANSWER_UPDATED','ANSWER_STAFF_INPUT')) FROM actions
            """, (r, n) -> new long[] {r.getLong(1), r.getLong(2), r.getLong(3)});
    }
}
