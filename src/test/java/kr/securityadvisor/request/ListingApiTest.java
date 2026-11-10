package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.jayway.jsonpath.JsonPath;
import java.util.HashSet;
import java.util.List;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 100건 경계를 넘는 합성 데이터로 HTTP 계약·권한·정렬·집계를 함께 검증한다. */
@Tag("docker")
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ListingApiTest {
    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("205건 전체 페이지·뒤쪽 검색·사업 범위·역할별 집계·정렬·잘못된 조건을 검증한다")
    void requests() throws Exception {
        long caseId = createCase("listing-owner", "목록합성-검색뒤쪽");
        for (int i = 0; i < 205; i++) {
            long id = createRequest(caseId, "SYNTHETIC_" + i);
            if (i < 5)
                jdbc.update(
                        "INSERT INTO assignments(security_request_id,field,assignee_username) VALUES (?,'INFRA','listing-infra')",
                        id);
        }
        createRequest(createCase("other-owner", "목록합성-비공개"), "OTHER");
        HashSet<Number> ids = new HashSet<>();
        for (int page = 0; page < 11; page++) {
            String body =
                    get("listing-owner", "APPLICANT", "/api/security-requests?size=20&page=" + page + "&sort=id,asc");
            assertThat((Number) JsonPath.read(body, "$.totalElements")).isEqualTo(205);
            assertThat((Number) JsonPath.read(body, "$.summary.mine")).isEqualTo(205);
            List<Number> rows = JsonPath.read(body, "$.content[*].securityRequestId");
            assertThat(rows).hasSize(page == 10 ? 5 : 20);
            rows.forEach(id -> assertThat(ids.add(id)).isTrue());
        }
        assertThat(ids).hasSize(205);
        assertThat((Number) JsonPath.read(
                        get("listing-owner", "APPLICANT", "/api/security-requests?query=SYNTHETIC_204"),
                        "$.totalElements"))
                .isEqualTo(1);
        assertThat((Number) JsonPath.read(get("listing-infra", "INFRA", "/api/security-requests"), "$.totalElements"))
                .isEqualTo(5);
        assertThat((Number)
                        JsonPath.read(get("listing-security", "SECURITY", "/api/security-requests"), "$.totalElements"))
                .isEqualTo(0);
        assertThat((Number) JsonPath.read(
                        get("other-owner", "APPLICANT", "/api/security-requests?businessCaseId=" + caseId),
                        "$.totalElements"))
                .isEqualTo(0);
        String asc = get("listing-owner", "APPLICANT", "/api/security-requests?size=1&sort=id,asc");
        String desc = get("listing-owner", "APPLICANT", "/api/security-requests?size=1&sort=id,desc");
        assertThat((Number) JsonPath.read(asc, "$.content[0].securityRequestId"))
                .isNotEqualTo((Number) JsonPath.read(desc, "$.content[0].securityRequestId"));
        assertThat(call("listing-owner", "APPLICANT", "/api/security-requests?size=101"))
                .hasStatus(400);
        assertThat(call("listing-owner", "APPLICANT", "/api/security-requests?sort=untrusted,desc"))
                .hasStatus(400);
    }

    @Test
    @DisplayName("135건 이력의 마지막 페이지·역할 제한·보안 메타 제외·동시 추가 중 페이지 기준을 검증한다")
    void audit() throws Exception {
        long id = createRequest(createCase("history-owner", "이력합성"), "SYNTHETIC");
        jdbc.update(
                "INSERT INTO assignments(security_request_id,field,assignee_username) VALUES (?,'INFRA','history-infra'),(?,'SECURITY','history-security')",
                id,
                id);
        for (int i = 0; i < 135; i++) event(id, "ANSWER_UPDATED", "이력합성-" + i);
        event(id, "DOCUMENT_DOWNLOADED", "보안 메타 합성");
        String filter = "/api/audit-events?targetType=SECURITY_REQUEST&targetId=" + id;
        String first = get("history-owner", "APPLICANT", filter + "&size=100");
        assertThat((Number) JsonPath.read(first, "$.totalElements")).isEqualTo(135);
        Number through = JsonPath.read(first, "$.throughId");
        event(id, "ANSWER_UPDATED", "동시 추가");
        String last = get("history-owner", "APPLICANT", filter + "&size=100&page=1&throughId=" + through);
        assertThat((List<?>) JsonPath.read(last, "$.content")).hasSize(35);
        HashSet<Number> all = new HashSet<>(JsonPath.<List<Number>>read(first, "$.content[*].auditEventId"));
        for (Number value : JsonPath.<List<Number>>read(last, "$.content[*].auditEventId"))
            assertThat(all.add(value)).isTrue();
        assertThat(all).hasSize(135);
        assertThat((Number)
                        JsonPath.read(get("history-owner", "APPLICANT", filter + "&query=이력합성-0"), "$.totalElements"))
                .isEqualTo(1);
        for (String role : List.of("INFRA", "SECURITY"))
            assertThat((Number) JsonPath.read(get("history-" + role.toLowerCase(), role, filter), "$.totalElements"))
                    .isEqualTo(137);
        assertThat((Number) JsonPath.read(get("someone", "APPLICANT", filter), "$.totalElements"))
                .isEqualTo(0);
        assertThat((Number) JsonPath.read(get("unassigned", "INFRA", filter), "$.totalElements"))
                .isEqualTo(0);
        assertThat((Number) JsonPath.read(
                        get("history-owner", "APPLICANT", filter + "&action=DOCUMENT_DOWNLOADED"), "$.totalElements"))
                .isEqualTo(0);
        assertThat((Number)
                        JsonPath.read(get("admin", "ADMIN", filter + "&action=DOCUMENT_DOWNLOADED"), "$.totalElements"))
                .isEqualTo(1);
    }

    private long createCase(String owner, String title) {
        return jdbc.queryForObject(
                "INSERT INTO business_cases(applicant_username,title,publication_id) VALUES (?,?,1) RETURNING business_case_id",
                Long.class,
                owner,
                title);
    }

    private long createRequest(long caseId, String type) {
        return jdbc.queryForObject(
                "INSERT INTO security_requests(business_case_id,doc_type,stage,status,created_at,updated_at) VALUES (?,?,1,'DRAFTING','2026-11-01T00:00:00Z','2026-11-01T00:00:00Z') RETURNING security_request_id",
                Long.class,
                caseId,
                type);
    }

    private void event(long id, String action, String reason) {
        jdbc.update(
                "INSERT INTO audit_events(occurred_at,actor_username,actor_role,target_type,target_id,action,reason) VALUES ('2026-11-01T00:00:00Z','history-owner','APPLICANT','SECURITY_REQUEST',?,?,?)",
                String.valueOf(id),
                action,
                reason);
    }

    private MvcTestResult call(String user, String role, String url) {
        return mvc.get()
                .uri(url)
                .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                        new MemberPrincipal(user, "합성 계정", MemberRole.valueOf(role)),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)))))
                .exchange();
    }

    private String get(String user, String role, String url) throws Exception {
        MvcTestResult result = call(user, role, url);
        assertThat(result).hasStatusOk();
        return result.getResponse().getContentAsString();
    }
}
