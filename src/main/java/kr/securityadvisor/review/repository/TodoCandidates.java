package kr.securityadvisor.review.repository;

import java.util.List;
import kr.securityadvisor.common.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 종료 신청과 다른 사람의 업무를 DB에서 제외한 뒤 200건씩 도메인 판정에 전달한다. */
@Repository
@RequiredArgsConstructor
public class TodoCandidates {
    private final JdbcTemplate jdbc;

    public List<Long> after(MemberPrincipal actor, long after) {
        return jdbc.queryForList(
                """
            SELECT r.security_request_id FROM security_requests r JOIN business_cases b USING(business_case_id)
            WHERE r.security_request_id>? AND r.status NOT IN ('CONFIRMED','CLOSED','WITHDRAWN')
            AND r.stage>=2 AND (?='ADMIN' OR (?='APPLICANT' AND b.applicant_username=?) OR
              (? IN ('INFRA','SECURITY') AND EXISTS(SELECT 1 FROM assignments a JOIN reviewer_pools p
                ON p.member_username=a.assignee_username AND p.field=a.field AND p.active=true
                WHERE a.security_request_id=r.security_request_id AND a.active=true AND a.assignee_username=? AND a.field=?)))
            ORDER BY r.security_request_id LIMIT 200
            """,
                Long.class,
                after,
                actor.role().name(),
                actor.role().name(),
                actor.username(),
                actor.role().name(),
                actor.username(),
                actor.role().name());
    }
}
