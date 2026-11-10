package kr.securityadvisor.request;

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
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 목록·사업·하위 자원·직접 문서 주소가 같은 배정 경계를 지키는지 확인한다. */
@Tag("docker")
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RequestAccessApiTest {
    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("담당자·관리자는 신청자 업무로 우회해 자기 미배정 사업을 만들 수 없다")
    void onlyApplicantCreatesCases() {
        for (String role : List.of("INFRA", "SECURITY", "ADMIN")) {
            assertThat(mvc.post()
                            .uri("/api/business-cases")
                            .with(authentication(auth(role.toLowerCase(), role)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"거부할 합성 신청\",\"docTypes\":[\"FIREWALL\"]}"))
                    .hasStatus(403);
        }
    }

    @Test
    @DisplayName("미배정·타인 자원은 404, 배정 신청은 허용하며 같은 사업의 미배정 형제 신청·문서는 숨긴다")
    void resourceBoundary() throws Exception {
        String created = post(
                "applicant",
                "APPLICANT",
                "/api/business-cases",
                "{\"title\":\"합성 권한 경계\",\"docTypes\":[\"FIREWALL\",\"SECURITY_REVIEW\"]}");
        long caseId = number(created, "$.businessCaseId");
        long requestId = number(created, "$.requests[0].securityRequestId");
        long sibling = number(created, "$.requests[1].securityRequestId");
        long document = number(
                post(
                        "applicant",
                        "APPLICANT",
                        "/api/security-requests/" + requestId + "/generated-documents",
                        "{\"includeAiSupplement\":false}"),
                "$.generatedDocumentId");
        long siblingDocument = number(
                post(
                        "applicant",
                        "APPLICANT",
                        "/api/security-requests/" + sibling + "/generated-documents",
                        "{\"includeAiSupplement\":false}"),
                "$.generatedDocumentId");
        for (String role : List.of("INFRA", "SECURITY")) {
            deniedResources(role.toLowerCase(), role, caseId, requestId, document);
        }
        deniedResources("applicant2", "APPLICANT", caseId, requestId, document);
        post("applicant", "APPLICANT", "/api/security-requests/" + requestId + "/reviews", "{\"expectedRevision\":0}");
        for (String role : List.of("INFRA", "SECURITY")) {
            String user = role.toLowerCase();
            assertThat(get(user, role, "/api/security-requests/" + requestId)).hasStatusOk();
            assertThat(get(user, role, "/api/generated-documents/" + document + "/download?format=PDF"))
                    .hasStatusOk();
            assertThat(get(user, role, "/api/business-cases/" + caseId))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.requests.length()")
                    .isEqualTo(1);
            assertThat(get(user, role, "/api/security-requests?businessCaseId=" + caseId))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .isEqualTo(1);
            assertThat(get(user, role, "/api/security-requests/" + sibling)).hasStatus(404);
            assertThat(get(user, role, "/api/generated-documents/" + siblingDocument + "/download?format=PDF"))
                    .hasStatus(404);
            assertThat(get(user, role, "/api/audit-events?targetType=SECURITY_REQUEST&targetId=" + sibling))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.totalElements")
                    .isEqualTo(0);
        }
        assertThat(get("admin", "ADMIN", "/api/business-cases/" + caseId))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.requests.length()")
                .isEqualTo(2);
        assertThat(get("admin", "ADMIN", "/api/generated-documents/" + siblingDocument))
                .hasStatusOk();

        // 재배정으로 이전 활성 배정을 종료하면 같은 세션에서도 바로 조회 권한을 잃는다.
        jdbc.update("UPDATE assignments SET active=false WHERE security_request_id=? AND field='INFRA'", requestId);
        deniedResources("infra", "INFRA", caseId, requestId, document);
        assertThat(get("infra", "INFRA", "/api/security-requests?businessCaseId=" + caseId))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.totalElements")
                .isEqualTo(0);
        assertThat(get("infra", "INFRA", "/api/audit-events?targetType=SECURITY_REQUEST&targetId=" + requestId))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.totalElements")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("현재 역할과 다른 분야의 과거 배정은 목록·상세·이력·처리할 일에 권한을 주지 않는다")
    void assignmentMustMatchCurrentRole() throws Exception {
        String created = post(
                "applicant",
                "APPLICANT",
                "/api/business-cases",
                "{\"title\":\"합성 역할 경계\",\"docTypes\":[\"FIREWALL\"]}");
        long caseId = number(created, "$.businessCaseId");
        long requestId = number(created, "$.requests[0].securityRequestId");
        jdbc.update(
                "INSERT INTO assignments(security_request_id,field,assignee_username) VALUES (?,'SECURITY','infra')",
                requestId);
        assertThat(get("infra", "INFRA", "/api/security-requests/" + requestId)).hasStatus(404);
        assertThat(get("infra", "INFRA", "/api/business-cases/" + caseId)).hasStatus(404);
        assertThat(get("infra", "INFRA", "/api/security-requests?businessCaseId=" + caseId))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.totalElements")
                .isEqualTo(0);
        assertThat(get("infra", "INFRA", "/api/audit-events?targetType=BUSINESS_CASE&targetId=" + caseId))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.totalElements")
                .isEqualTo(0);
    }

    private void deniedResources(String user, String role, long caseId, long requestId, long document) {
        for (String path : List.of(
                "/api/business-cases/" + caseId,
                "/api/business-cases/" + caseId + "/uploaded-files",
                "/api/security-requests/" + requestId,
                "/api/security-requests/" + requestId + "/reviews",
                "/api/security-requests/" + requestId + "/assignments",
                "/api/security-requests/" + requestId + "/supplements",
                "/api/security-requests/" + requestId + "/submissions",
                "/api/security-requests/" + requestId + "/documents",
                "/api/security-requests/" + requestId + "/firewall-rules",
                "/api/generated-documents/" + document,
                "/api/generated-documents/" + document + "/download?format=PDF")) {
            assertThat(get(user, role, path)).as(path).hasStatus(404);
        }
    }

    private MvcTestResult get(String user, String role, String path) {
        return mvc.get().uri(path).with(authentication(auth(user, role))).exchange();
    }

    private String post(String user, String role, String path, String body) throws Exception {
        var result = mvc.post()
                .uri(path)
                .with(authentication(auth(user, role)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        assertThat(result).hasStatus(201);
        return result.getResponse().getContentAsString();
    }

    private static long number(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private static Authentication auth(String user, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new MemberPrincipal(user, "합성 계정", MemberRole.valueOf(role)),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
