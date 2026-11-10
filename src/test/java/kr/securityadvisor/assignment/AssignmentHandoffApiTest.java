package kr.securityadvisor.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/** 계정·분야 등록 변경 뒤에도 기존 배정은 보존되고 관리자 집계·상세가 인계 필요 상태를 일치시킨다. */
@Tag("docker")
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AssignmentHandoffApiTest {
    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("역할 변경 직후 목록·상세·할일에 재배정 필요가 표시되고 인계 뒤 답변·협의·이력이 보존된다")
    void roleChangeAndHandoff() throws Exception {
        long pool = register("handoff.old");
        register("handoff.new");
        long id = request();
        assign(id, "handoff.old");
        long reviewId = JsonPath.<List<Number>>read(
                        get("admin", "/api/security-requests/" + id + "/reviews"), "$[?(@.field=='INFRA')].reviewId")
                .get(0)
                .longValue();
        put(
                "handoff.old",
                "/api/reviews/" + reviewId + "/answers/system.target_server",
                "{\"value\":\"합성 인계 서버\"}",
                200);
        String reviewsBefore = get("admin", "/api/security-requests/" + id + "/reviews");
        String requestBefore = get("admin", "/api/security-requests/" + id);
        long before = number(get("admin", "/api/todo-summary"), "$.unassignedRequests");
        Authentication oldSession = auth("handoff.old");
        put("admin", "/api/members/handoff.old/role", "{\"role\":\"SECURITY\",\"reason\":\"합성 전보\"}", 200);
        assertIssue(id, "ROLE_CHANGED");
        assertThat(number(get("admin", "/api/todo-summary"), "$.unassignedRequests"))
                .isEqualTo(before + 1);
        assertThat(mvc.get().uri("/api/security-requests/" + id).with(authentication(oldSession)))
                .hasStatus(401);
        put("admin", "/api/reviewer-pools/" + pool + "/default", "", 422);
        assign(id, "handoff.new");
        assertIssue(id, null);
        assertThat(get("admin", "/api/security-requests/" + id)).isEqualTo(requestBefore);
        assertThat(get("admin", "/api/security-requests/" + id + "/reviews")).isEqualTo(reviewsBefore);
        assertThat(number(get("admin", "/api/todo-summary"), "$.unassignedRequests"))
                .isEqualTo(before);
        long caseId = number(get("handoff.new", "/api/security-requests/" + id), "$.businessCaseId");
        assertThat(get("handoff.new", "/api/business-cases/" + caseId)).contains("합성 인계 서버");
        assertThat(mvc.get().uri("/api/security-requests/" + id).with(authentication(auth("handoff.old"))))
                .hasStatus(404);
        List<Boolean> oldActive = JsonPath.read(
                get("admin", "/api/security-requests/" + id + "/assignments"),
                "$[?(@.assigneeUsername=='handoff.old')].active");
        assertThat(oldActive).containsExactly(false);
    }

    @Test
    @DisplayName("계정·담당자 등록 비활성을 구분하고 종료 신청의 담당자를 보존한다")
    void inactiveAndTerminal() throws Exception {
        long pool = register("handoff.inactive");
        long id = request();
        assign(id, "handoff.inactive");
        put("admin", "/api/members/handoff.inactive/activation", "{\"active\":false,\"reason\":\"합성 휴직\"}", 200);
        assertIssue(id, "ACCOUNT_INACTIVE");
        put("admin", "/api/reviewer-pools/" + pool + "/default", "", 422);
        put("admin", "/api/members/handoff.inactive/activation", "{\"active\":true,\"reason\":\"합성 복귀\"}", 200);
        assertIssue(id, null);
        put(
                "admin",
                "/api/reviewer-pools/" + pool + "/activation",
                "{\"active\":false,\"reason\":\"합성 담당 업무 종료\"}",
                200);
        assertIssue(id, "POOL_INACTIVE");
        String pools = get("admin", "/api/reviewer-pools");
        assertThat(JsonPath.<List<Boolean>>read(pools, "$[?(@.memberUsername=='handoff.inactive')].eligible"))
                .containsExactly(false);
        put(
                "applicant",
                "/api/security-requests/" + id + "/withdrawal",
                "{\"reason\":\"합성 종료\",\"expectedRevision\":"
                        + number(get("applicant", "/api/security-requests/" + id), "$.revision") + "}",
                200);
        assertThat(number(get("admin", "/api/security-requests?inbox=mine&query=" + id), "$.totalElements"))
                .isZero();
        post(
                "admin",
                "/api/security-requests/" + id + "/assignments",
                "{\"field\":\"INFRA\",\"assigneeUsername\":\"infra\",\"reason\":\"종료 신청 인계 금지 검사\"}",
                422);
    }

    @Test
    @DisplayName("등록 누락은 2·3·4단계에서 일관되게 인계 대상으로 표시하고 확정 신청은 제외한다")
    void missingPoolAcrossStages() throws Exception {
        long pool = register("handoff.missing");
        long id = request();
        assign(id, "handoff.missing");
        entityManager.flush();
        jdbc.update("delete from reviewer_pools where reviewer_pool_id=?", pool);
        entityManager.clear();
        assertIssue(id, "NOT_REGISTERED");
        long count = number(get("admin", "/api/todo-summary"), "$.unassignedRequests");
        for (int stage : List.of(3, 4)) {
            jdbc.update(
                    "update security_requests set stage=?, status=? where security_request_id=?",
                    stage,
                    stage == 3 ? "FINAL_REVIEW" : "SUBMITTED",
                    id);
            entityManager.clear();
            assertIssue(id, "NOT_REGISTERED");
            assertThat(number(get("admin", "/api/todo-summary"), "$.unassignedRequests"))
                    .isEqualTo(count);
        }
        jdbc.update("update security_requests set status='CONFIRMED' where security_request_id=?", id);
        entityManager.clear();
        assertThat(number(get("admin", "/api/todo-summary"), "$.unassignedRequests"))
                .isEqualTo(count - 1);
        assertThat(number(get("admin", "/api/security-requests?inbox=mine&query=" + id), "$.totalElements"))
                .isZero();
    }

    @Test
    @DisplayName("같은 사업의 기존 담당자가 부적격이면 유효한 기본 담당자로 자동 배정한다")
    void siblingFallback() throws Exception {
        long oldPool = register("handoff.sibling");
        long newPool = register("handoff.default");
        put("admin", "/api/reviewer-pools/" + oldPool + "/default", "", 200);
        String business = post(
                "applicant",
                "/api/business-cases",
                "{\"title\":\"합성 인계 사업\",\"docTypes\":[\"FIREWALL\",\"SECURITY_REVIEW\"]}",
                201);
        long first = number(business, "$.requests[0].securityRequestId"),
                second = number(business, "$.requests[1].securityRequestId");
        post("applicant", "/api/security-requests/" + first + "/reviews", "{\"expectedRevision\":0}", 201);
        put("admin", "/api/reviewer-pools/" + newPool + "/default", "", 200);
        put("admin", "/api/reviewer-pools/" + oldPool + "/activation", "{\"active\":false,\"reason\":\"합성 휴가\"}", 200);
        post("applicant", "/api/security-requests/" + second + "/reviews", "{\"expectedRevision\":0}", 201);
        assertThat(JsonPath.<List<String>>read(
                        get("admin", "/api/security-requests/" + second + "/assignments"),
                        "$[?(@.field=='INFRA' && @.active == true)].assigneeUsername"))
                .containsExactly("handoff.default");
        assertIssue(first, "POOL_INACTIVE");
    }

    private void assertIssue(long id, String issue) throws Exception {
        String listing = get("admin", "/api/security-requests?query=" + id);
        assertThat(JsonPath.<List<String>>read(
                        listing,
                        "$.content[?(@.securityRequestId==" + id
                                + ")].overview.fields[?(@.field=='INFRA')].reassignmentReason"))
                .containsExactly(issue);
        String assignments = get("admin", "/api/security-requests/" + id + "/assignments");
        assertThat(JsonPath.<List<String>>read(
                        assignments, "$[?(@.field=='INFRA' && @.active == true)].reassignmentReason"))
                .containsExactly(issue);
        assertThat(number(get("admin", "/api/security-requests?inbox=mine&query=" + id), "$.totalElements"))
                .isEqualTo(issue == null ? 0 : 1);
    }

    private long request() throws Exception {
        String c =
                post("applicant", "/api/business-cases", "{\"title\":\"합성 담당자 인계\",\"docTypes\":[\"FIREWALL\"]}", 201);
        long id = number(c, "$.requests[0].securityRequestId");
        post("applicant", "/api/security-requests/" + id + "/reviews", "{\"expectedRevision\":0}", 201);
        return id;
    }

    private long register(String username) throws Exception {
        post(
                null,
                "/api/members",
                "{\"username\":\"" + username + "\",\"displayName\":\"합성 인계 담당자\",\"password\":\"Synthetic-pass1\"}",
                201);
        put("admin", "/api/members/" + username + "/role", "{\"role\":\"INFRA\",\"reason\":\"합성 인계\"}", 200);
        return number(
                post(
                        "admin",
                        "/api/reviewer-pools",
                        "{\"field\":\"INFRA\",\"memberUsername\":\"" + username + "\",\"defaultReviewer\":false}",
                        201),
                "$.reviewerPoolId");
    }

    private void assign(long id, String username) throws Exception {
        post(
                "admin",
                "/api/security-requests/" + id + "/assignments",
                "{\"field\":\"INFRA\",\"assigneeUsername\":\"" + username + "\",\"reason\":\"합성 업무 인계\"}",
                201);
    }

    private Authentication auth(String username) {
        entityManager.flush();
        var row = jdbc.queryForMap("SELECT role,session_version FROM members WHERE username=?", username);
        MemberRole role = MemberRole.valueOf((String) row.get("role"));
        return new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(username, username, role, ((Number) row.get("session_version")).longValue()),
                null,
                List.of(new SimpleGrantedAuthority(role.authority())));
    }

    private String get(String user, String path) throws Exception {
        var result = mvc.get().uri(path).with(authentication(auth(user))).exchange();
        assertThat(result).hasStatusOk();
        return result.getResponse().getContentAsString();
    }

    private String post(String user, String path, String body, int code) throws Exception {
        var request = mvc.post()
                .uri(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (user != null) request.with(authentication(auth(user)));
        var result = request.exchange();
        assertThat(result).hasStatus(code);
        return result.getResponse().getContentAsString();
    }

    private void put(String user, String path, String body, int code) {
        assertThat(mvc.put()
                        .uri(path)
                        .with(authentication(auth(user)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .hasStatus(code);
    }

    private long number(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }
}
