package kr.securityadvisor.request.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.dto.RequestListQuery;
import kr.securityadvisor.request.dto.RequestListResponse;
import kr.securityadvisor.request.dto.RequestOverview;
import kr.securityadvisor.request.dto.SecurityRequestListItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 변경 경로와 분리된 읽기 전용 조회. 바인딩한 조건으로 DB에서 페이지·집계를 계산한다(ADR 0009). */
@Repository
@RequiredArgsConstructor
public class RequestListingRepository {
    private final EntityManager entityManager;
    private final ObjectMapper json;
    private static final String BASE = """
WITH visible AS (
 SELECT r.*, c.title AS case_title, c.department, c.applicant_username,
   COALESCE(t.doc->>'name', r.doc_type) AS doc_label,
   COALESCE(t.doc->'reviewFields', CAST('["INFRA","SECURITY"]' AS jsonb)) AS required_fields,
   (SELECT max(s.submission_id) FROM submissions s WHERE s.security_request_id=r.security_request_id) AS submission_id,
   (SELECT min(d.due_date) FROM deadlines d WHERE d.security_request_id=r.security_request_id AND d.active
      AND (:role NOT IN ('INFRA','SECURITY') OR d.field IS NULL OR d.field=:role)) AS due_date
 FROM security_requests r JOIN business_cases c USING (business_case_id)
 JOIN publications p USING (publication_id)
 LEFT JOIN LATERAL (SELECT doc FROM jsonb_array_elements(p.field_schema->'docTypes') doc
       WHERE doc->>'code'=r.doc_type LIMIT 1) t ON true
 WHERE (:role='ADMIN' OR (:role='APPLICANT' AND c.applicant_username=:username)
   OR (:role IN ('INFRA','SECURITY') AND EXISTS (SELECT 1 FROM assignments a
       WHERE a.security_request_id=r.security_request_id AND a.active AND a.field=:role AND a.assignee_username=:username)))
 AND (CAST(:caseId AS bigint) IS NULL OR r.business_case_id=CAST(:caseId AS bigint))
 AND (CAST(:status AS text) IS NULL OR r.status=CAST(:status AS text))
 AND (CAST(:docType AS text) IS NULL OR r.doc_type=CAST(:docType AS text))
 AND (CAST(:fromDate AS date) IS NULL OR r.created_at >= CAST(CAST(:fromDate AS date) AS timestamp) AT TIME ZONE 'Asia/Seoul')
 AND (CAST(:toDate AS date) IS NULL OR r.created_at < CAST((CAST(:toDate AS date)+1) AS timestamp) AT TIME ZONE 'Asia/Seoul')
 AND (:query='' OR position(lower(:query) in lower(concat_ws(' ', c.title, c.department,
       c.applicant_username, r.security_request_id, r.doc_type, t.doc->>'name'))) > 0)
 AND (:supplementState='all' OR EXISTS (SELECT 1 FROM supplements x
       WHERE x.security_request_id=r.security_request_id AND (:supplementState='any'
          OR (:supplementState='pending' AND x.status<>'RESOLVED')
          OR (:supplementState='resolved' AND x.status='RESOLVED'))))
), enriched AS (
 SELECT v.*, (SELECT jsonb_agg(jsonb_build_object('field', f.field,
       'assigneeUsername', a.assignee_username, 'reviewStatus', rv.status,
       'allocated', rv.allocated_at IS NOT NULL, 'confirmed', fc.final_confirmation_id IS NOT NULL,
       'reassignmentReason', CASE
         WHEN v.stage<2 OR v.status IN ('CONFIRMED','CLOSED','WITHDRAWN') THEN NULL
         WHEN a.assignee_username IS NULL THEN 'UNASSIGNED'
         WHEN m.username IS NULL OR NOT m.active THEN 'ACCOUNT_INACTIVE'
         WHEN m.role<>f.field THEN 'ROLE_CHANGED'
         WHEN rp.reviewer_pool_id IS NULL THEN 'NOT_REGISTERED'
         WHEN NOT rp.active THEN 'POOL_INACTIVE' ELSE NULL END)
       ORDER BY f.field)
   FROM jsonb_array_elements_text(v.required_fields) f(field)
   LEFT JOIN assignments a ON a.security_request_id=v.security_request_id AND a.field=f.field AND a.active
   LEFT JOIN members m ON m.username=a.assignee_username
   LEFT JOIN reviewer_pools rp ON rp.member_username=a.assignee_username AND rp.field=f.field
   LEFT JOIN reviews rv ON rv.security_request_id=v.security_request_id AND rv.field=f.field
   LEFT JOIN final_confirmations fc ON fc.submission_id=v.submission_id AND fc.field=f.field) AS fields,
   EXISTS(SELECT 1 FROM answers a WHERE (a.security_request_id=v.security_request_id
      OR (a.scope='CASE' AND a.business_case_id=v.business_case_id)) AND a.needs_review) AS needs_review
 FROM visible v
 WHERE (:kind='all' OR (:kind='finalized' AND v.status='CONFIRMED')
   OR (:kind='submitted' AND v.status<>'CONFIRMED' AND v.submission_id IS NOT NULL)
   OR (:kind='draft' AND v.status<>'CONFIRMED' AND v.submission_id IS NULL))
), listed AS (
 SELECT e.*, (e.status NOT IN ('CONFIRMED','CLOSED','WITHDRAWN') AND CASE
   WHEN :role='APPLICANT' THEN e.status IN ('DRAFTING','SUPPLEMENT_REQUESTED','FINAL_REVIEW')
   WHEN :role='ADMIN' THEN e.stage>=2 AND EXISTS (SELECT 1 FROM jsonb_array_elements(e.fields) f
          WHERE f->>'reassignmentReason' IS NOT NULL)
   ELSE EXISTS (SELECT 1 FROM jsonb_array_elements(e.fields) f
       WHERE f->>'field'=:role AND f->>'assigneeUsername'=:username AND (
         (e.stage=2 AND COALESCE(f->>'reviewStatus','WAITING')<>'COMPLETED'
           AND NOT EXISTS (SELECT 1 FROM supplements x WHERE x.security_request_id=e.security_request_id
               AND x.field=:role AND x.status='OPEN')
           AND (:role='INFRA' OR NOT EXISTS (SELECT 1 FROM jsonb_array_elements(e.fields) i
                 WHERE i->>'field'='INFRA' AND i->>'allocated'='false')))
         OR (e.stage=4 AND e.submission_id IS NOT NULL
           AND (:role='INFRA' OR NOT EXISTS (SELECT 1 FROM jsonb_array_elements(e.fields) i
                 WHERE i->>'field'='INFRA' AND i->>'confirmed'='false'))
           AND (f->>'confirmed'='false' OR (:role=CASE WHEN e.required_fields @> CAST('["SECURITY"]' AS jsonb)
                 THEN 'SECURITY' ELSE 'INFRA' END
             AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(e.fields) i WHERE i->>'confirmed'='false'))))
       )) END) AS my_turn
 FROM enriched e
)
""";
    private static final String INBOX =
            " WHERE (:inbox='all' OR (:inbox='mine' AND my_turn) OR (:inbox='staff' AND status IN ('IN_REVIEW','SUBMITTED')) OR (:inbox='done' AND status='CONFIRMED')) ";

    /** 전체 엔티티 로딩 없이 검색 건수와 해당 페이지의 표시 요약만 반환한다. */
    public RequestListResponse search(MemberPrincipal member, RequestListQuery filter) {
        Tuple counts = (Tuple) bind(
                        entityManager.createNativeQuery(
                                BASE
                                        + "SELECT count(*) AS all_count, count(*) FILTER (WHERE my_turn) AS mine_count, count(*) FILTER (WHERE status IN ('IN_REVIEW','SUBMITTED')) AS staff_count, count(*) FILTER (WHERE status='CONFIRMED') AS done_count FROM listed",
                                Tuple.class),
                        member,
                        filter)
                .getSingleResult();
        RequestListResponse.Summary summary = new RequestListResponse.Summary(
                number(counts, "all_count"),
                number(counts, "mine_count"),
                number(counts, "staff_count"),
                number(counts, "done_count"));
        long total = switch (filter.inbox()) {
            case "mine" -> summary.mine();
            case "staff" -> summary.staff();
            case "done" -> summary.done();
            default -> summary.all();
        };
        String order = switch (filter.sort()) {
            case "createdAt,asc" -> "created_at ASC, security_request_id ASC";
            case "updatedAt,asc" -> "updated_at ASC, security_request_id ASC";
            case "updatedAt,desc" -> "updated_at DESC, security_request_id DESC";
            case "id,asc" -> "security_request_id ASC";
            case "id,desc" -> "security_request_id DESC";
            case "dueDate,asc" -> "due_date ASC NULLS LAST, created_at DESC, security_request_id DESC";
            default -> "created_at DESC, security_request_id DESC";
        };
        // order는 위 상수 중 하나이며 사용자 문자열을 SQL에 삽입하지 않는다.
        Query query = bind(
                entityManager.createNativeQuery(
                        BASE
                                + "SELECT security_request_id,business_case_id,case_title,department,applicant_username,doc_type,stage,status,revision,updated_at,created_at,my_turn,due_date,submission_id,CAST(fields AS text) AS field_json,needs_review FROM listed"
                                + INBOX + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
                        Tuple.class),
                member,
                filter);
        query.setParameter("inbox", filter.inbox())
                .setParameter("limit", filter.size())
                .setParameter("offset", (long) filter.page() * filter.size());
        @SuppressWarnings("unchecked")
        List<Tuple> rows = query.getResultList();
        return new RequestListResponse(
                rows.stream().map(this::item).toList(), filter.page(), filter.size(), total, summary);
    }

    private Query bind(Query q, MemberPrincipal member, RequestListQuery f) {
        return q.setParameter("role", member.role().name())
                .setParameter("username", member.username())
                .setParameter("query", f.query())
                .setParameter("status", f.status())
                .setParameter("docType", f.docType())
                .setParameter("caseId", f.businessCaseId())
                .setParameter("fromDate", f.from())
                .setParameter("toDate", f.to())
                .setParameter("kind", f.documentKind())
                .setParameter("supplementState", f.supplementState());
    }

    private SecurityRequestListItem item(Tuple t) {
        RequestStatus status = RequestStatus.valueOf(t.get("status", String.class));
        List<RequestOverview.FieldSummary> fields =
                json.readValue(t.get("field_json", String.class), new TypeReference<>() {});
        return new SecurityRequestListItem(
                number(t, "security_request_id"),
                number(t, "business_case_id"),
                t.get("case_title", String.class),
                t.get("department", String.class),
                t.get("applicant_username", String.class),
                DocTypeCode.of(t.get("doc_type", String.class)),
                ((Number) t.get("stage")).intValue(),
                status,
                status.label(),
                number(t, "revision"),
                instant(t.get("updated_at")),
                instant(t.get("created_at")),
                new RequestOverview(
                        t.get("my_turn", Boolean.class),
                        t.get("due_date") == null
                                ? null
                                : LocalDate.parse(t.get("due_date").toString()),
                        t.get("submission_id") == null ? null : number(t, "submission_id"),
                        fields,
                        t.get("needs_review", Boolean.class)));
    }

    private static long number(Tuple t, String key) {
        return ((Number) t.get(key)).longValue();
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        return ((java.sql.Timestamp) value).toInstant();
    }
}
