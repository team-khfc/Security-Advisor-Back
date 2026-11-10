package kr.securityadvisor.audit.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import kr.securityadvisor.audit.domain.AuditEvent;
import kr.securityadvisor.audit.dto.AuditEventResponse;
import kr.securityadvisor.audit.dto.AuditListQuery;
import kr.securityadvisor.audit.dto.AuditListResponse;
import kr.securityadvisor.common.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 대상의 소유·활성 배정을 DB에서 확인하며 권한 없는 기록은 집계에도 포함하지 않는다. */
@Repository
@RequiredArgsConstructor
public class AuditListingRepository {
    private final EntityManager entityManager;
    private static final String WHERE = """
 FROM audit_events e WHERE
 (:role='ADMIN' OR (
   (:role<>'APPLICANT' OR e.action NOT IN ('LOGIN_SUCCEEDED','LOGIN_FAILED','LOGIN_LOCKED',
      'DOCUMENT_DOWNLOADED','PRIVATE_CONTEXT_VIEWED','LLM_CALLED','LLM_CALL_FAILED'))
   AND EXISTS (SELECT 1 FROM business_cases c JOIN security_requests r USING(business_case_id)
     WHERE ((e.target_type='SECURITY_REQUEST' AND e.target_id=CAST(r.security_request_id AS text))
         OR (e.target_type='BUSINESS_CASE' AND e.target_id=CAST(c.business_case_id AS text)))
     AND ((:role='APPLICANT' AND c.applicant_username=:username)
       OR (:role IN ('INFRA','SECURITY') AND EXISTS (SELECT 1 FROM assignments a
         WHERE a.security_request_id=r.security_request_id AND a.active AND a.assignee_username=:username))))
 ))
 AND (CAST(:targetType AS text) IS NULL OR e.target_type=CAST(:targetType AS text))
 AND (CAST(:targetId AS text) IS NULL OR e.target_id=CAST(:targetId AS text))
 AND (CAST(:action AS text) IS NULL OR e.action=CAST(:action AS text))
 AND (:query='' OR position(lower(:query) in lower(concat_ws(' ',e.target_id,e.actor_username,e.action,e.reason,e.summary)))>0)
 """;
    private static final String BOUND = " AND e.audit_event_id<=:throughId ";
    /** 새 이력 추가 중에도 기존 페이지 경계를 유지한다. */
    public AuditListResponse search(MemberPrincipal member, AuditListQuery filter) {
        long through = filter.throughId() == null
                ? ((Number) bind(
                                        entityManager.createNativeQuery(
                                                "SELECT COALESCE(max(e.audit_event_id),0)" + WHERE),
                                        member,
                                        filter)
                                .getSingleResult())
                        .longValue()
                : filter.throughId();
        long count = ((Number) bind(entityManager.createNativeQuery("SELECT count(*)" + WHERE + BOUND), member, filter)
                        .setParameter("throughId", through)
                        .getSingleResult())
                .longValue();
        @SuppressWarnings("unchecked")
        List<AuditEvent> entries = bind(
                        entityManager.createNativeQuery(
                                "SELECT e.*" + WHERE + BOUND
                                        + " ORDER BY e.occurred_at DESC,e.audit_event_id DESC LIMIT :limit OFFSET :offset",
                                AuditEvent.class),
                        member,
                        filter)
                .setParameter("throughId", through)
                .setParameter("limit", filter.size())
                .setParameter("offset", (long) filter.page() * filter.size())
                .getResultList();
        return new AuditListResponse(
                entries.stream().map(AuditEventResponse::from).toList(), filter.page(), filter.size(), count, through);
    }
    /** 기존 배열 계약의 호환 조회도 같은 권한 필터를 적용한다. 새 화면은 search를 사용한다. */
    public List<AuditEventResponse> forTarget(MemberPrincipal member, String type, String id) {
        @SuppressWarnings("unchecked")
        List<AuditEvent> entries = bind(
                        entityManager.createNativeQuery(
                                "SELECT e.*" + WHERE + " ORDER BY e.occurred_at ASC,e.audit_event_id ASC",
                                AuditEvent.class),
                        member,
                        new AuditListQuery(type, id, null, null, null, 0, 20))
                .getResultList();
        return entries.stream().map(AuditEventResponse::from).toList();
    }

    private Query bind(Query q, MemberPrincipal member, AuditListQuery f) {
        return q.setParameter("role", member.role().name())
                .setParameter("username", member.username())
                .setParameter("targetType", f.targetType())
                .setParameter("targetId", f.targetId())
                .setParameter("action", f.action())
                .setParameter("query", f.query());
    }
}
