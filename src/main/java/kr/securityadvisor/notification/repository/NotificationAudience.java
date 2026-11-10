package kr.securityadvisor.notification.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.notification.domain.NotificationKind;
import kr.securityadvisor.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 알림 전용 읽기 모델. 권한 조건을 페이지 조회·건수·읽음 처리·발송에 동일하게 적용한다. */
@Repository
@RequiredArgsConstructor
public class NotificationAudience {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String ACCESS = """
        m.active = true AND (
          m.role = 'ADMIN'
          OR (m.role = 'APPLICANT' AND bc.applicant_username = m.username)
          OR (m.role IN ('INFRA', 'SECURITY') AND EXISTS (
            SELECT 1 FROM assignments a JOIN reviewer_pools p
              ON p.member_username = a.assignee_username AND p.field = a.field AND p.active = true
            WHERE a.security_request_id = r.security_request_id AND a.active = true
              AND a.assignee_username = m.username AND a.field = m.role)))
        """;
    private static final String FROM = """
        FROM notifications n JOIN members m ON m.username = n.recipient_username
        JOIN security_requests r ON r.security_request_id = n.security_request_id
        JOIN business_cases bc ON bc.business_case_id = r.business_case_id
        WHERE n.recipient_username = :username AND m.role = :role AND n.recipient_role = m.role
        AND
        """ + ACCESS;

    /** 링크는 알려진 업무 자원만 해석한다. 외부 URL이나 알 수 없는 대상은 발송하지 않는다. */
    public Long requestId(String link) {
        if (link == null) return null;
        var request = java.util.regex.Pattern.compile("^/(?:api/security-requests|requests)/(\\d+)(?:/.*)?$")
                .matcher(link);
        if (request.matches()) return Long.valueOf(request.group(1));
        var submission =
                java.util.regex.Pattern.compile("^/api/submissions/(\\d+)$").matcher(link);
        if (!submission.matches()) return null;
        return jdbc
                .query(
                        "SELECT security_request_id FROM submissions WHERE submission_id = :id",
                        Map.of("id", Long.valueOf(submission.group(1))),
                        (rs, row) -> rs.getLong(1))
                .stream()
                .findFirst()
                .orElse(null);
    }

    /** 발송 시점의 활성 계정·배정·분야 등록을 확인하고 수신 역할을 고정한다. */
    public Optional<String> eligibleRole(String username, Long requestId, NotificationKind kind) {
        if (requestId == null) return Optional.empty();
        String roleCondition = switch (kind) {
            case UNASSIGNED, REASSIGNMENT_REQUIRED -> " AND m.role = 'ADMIN'";
            case ASSIGNED, REASSIGNED, SUPPLEMENT_ANSWERED, SUBMITTED, REVIEW_RECHECK, COMMON_INFO_CHANGED ->
                " AND m.role IN ('INFRA','SECURITY')";
            case DEADLINE_IMMINENT, DEADLINE_OVERDUE -> " AND m.role <> 'ADMIN'";
            default -> " AND m.role = 'APPLICANT'";
        };
        return jdbc
                .query(
                        """
            SELECT m.role FROM members m CROSS JOIN security_requests r
            JOIN business_cases bc ON bc.business_case_id = r.business_case_id
            WHERE m.username = :username AND r.security_request_id = :id AND
            """ + ACCESS + roleCondition,
                        Map.of("username", username, "id", requestId),
                        (rs, row) -> rs.getString(1))
                .stream()
                .findFirst();
    }

    /** 총건수와 페이지에 같은 권한 조건을 적용해 접근 불가 알림 때문에 빈 페이지가 생기지 않는다. */
    public PageResponse<NotificationResponse> list(MemberPrincipal member, boolean unreadOnly, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("페이지는 0 이상, 크기는 1~100이어야 합니다");
        var params = new java.util.HashMap<String, Object>(parameters(member));
        params.put("limit", size);
        params.put("offset", (long) page * size);
        String where = FROM + (unreadOnly ? " AND n.read_at IS NULL" : "");
        long total = jdbc.queryForObject("SELECT count(*) " + where, params, Long.class);
        var content = jdbc.query(
                "SELECT n.* " + where + " ORDER BY n.notification_id DESC LIMIT :limit OFFSET :offset",
                params,
                (rs, row) -> new NotificationResponse(
                        rs.getLong("notification_id"),
                        NotificationKind.valueOf(rs.getString("kind")),
                        rs.getString("title"),
                        "/requests/" + rs.getLong("security_request_id"),
                        rs.getTimestamp("read_at") == null
                                ? null
                                : rs.getTimestamp("read_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()));
        return new PageResponse<>(content, page, size, total);
    }

    public long unreadCount(MemberPrincipal member) {
        return jdbc.queryForObject(
                "SELECT count(*) " + FROM + " AND n.read_at IS NULL", parameters(member), Long.class);
    }

    public boolean canRead(MemberPrincipal member, Long id) {
        var params = new java.util.HashMap<String, Object>(parameters(member));
        params.put("id", id);
        return jdbc.queryForObject("SELECT count(*) " + FROM + " AND n.notification_id = :id", params, Long.class) == 1;
    }

    /** 변경된 담당자가 맡고 있던 진행 중 업무만 관리자에게 안내한다. 종결 업무는 제외한다. */
    public List<Long> handoffRequests(String username) {
        return jdbc.query("""
            SELECT DISTINCT r.security_request_id FROM security_requests r
            JOIN assignments a ON a.security_request_id = r.security_request_id AND a.active = true
            JOIN members m ON m.username = a.assignee_username
            LEFT JOIN reviewer_pools p ON p.member_username = m.username AND p.field = a.field
            WHERE a.assignee_username = :username AND r.stage >= 2
              AND r.status NOT IN ('CONFIRMED','CLOSED','WITHDRAWN')
              AND (m.active = false OR m.role <> a.field OR p.reviewer_pool_id IS NULL OR p.active = false)
            """, Map.of("username", username), (rs, row) -> rs.getLong(1));
    }

    public List<String> admins() {
        return jdbc.query(
                "SELECT username FROM members WHERE role = 'ADMIN' AND active = true",
                Map.of(),
                (rs, row) -> rs.getString(1));
    }

    private Map<String, Object> parameters(MemberPrincipal member) {
        return Map.of("username", member.username(), "role", member.role().name());
    }
}
