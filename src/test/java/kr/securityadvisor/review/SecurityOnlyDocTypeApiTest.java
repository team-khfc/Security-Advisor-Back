package kr.securityadvisor.review;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.ObjectMapper;

/**
 * ADR 0005 데이터 서류 유형 — 관리자가 SECURITY 협의만 요구하는 서류 유형을 게시하면, INFRA 할당·INFRA 최종 확인 없이
 * 협의 완료 → 제출 → SECURITY 최종 확인 → 확정까지 코드 수정 없이 끝난다. INFRA 선행 규칙은 INFRA 가 요구 분야일 때만 적용된다.
 */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityOnlyDocTypeApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication INFRA = auth("infra", "박인프라", MemberRole.INFRA);
    private static final Authentication SECURITY = auth("security", "정보안", MemberRole.SECURITY);
    private static final Authentication ADMIN = auth("admin", "최관리", MemberRole.ADMIN);
    private static final String SECURITY_CHECKLIST =
            "{\"processed_info\":true,\"external_transfer\":true,\"access_control\":true,\"logging\":true}";

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("SECURITY 만 요구하는 서류 유형: 배정 1건 → INFRA 할당 없이 협의 완료 → 제출 → INFRA 최종 확인 422 → SECURITY 확인·확정")
    void securityOnlyFlow() throws Exception {
        publishSecurityOnlyDocType();

        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"보안 단독 검토(합성)\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"SEC_ONLY\"]}")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        Long requestId = ((Number)
                        JsonPath.read(created.getResponse().getContentAsString(), "$.requests[0].securityRequestId"))
                .longValue();

        MvcTestResult requested = mvc.post()
                .uri("/api/security-requests/{id}/reviews", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":" + requestRevision(requestId) + "}")
                .exchange();
        assertThat(requested).hasStatus(HttpStatus.CREATED);
        assertThat(requested).bodyJson().extractingPath("$.reviews[*].field").isEqualTo(List.of("SECURITY"));
        assertThat(requested)
                .bodyJson()
                .extractingPath("$.assignments.assigned[*].field")
                .isEqualTo(List.of("SECURITY"));
        Long reviewId = ((Number) ((List<?>) JsonPath.read(
                                requested.getResponse().getContentAsString(),
                                "$.reviews[?(@.field=='SECURITY')].reviewId"))
                        .get(0))
                .longValue();

        // INFRA 할당 없이 SECURITY 협의 완료 → 3단계
        long reviewRevision = ((Number) JsonPath.read(
                        mvc.get()
                                .uri("/api/reviews/{id}", reviewId)
                                .with(authentication(SECURITY))
                                .exchange()
                                .getResponse()
                                .getContentAsString(),
                        "$.revision"))
                .longValue();
        assertThat(mvc.put()
                        .uri("/api/reviews/{id}/completion", reviewId)
                        .with(authentication(SECURITY))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opinion\":\"검토 의견(합성)\",\"checklist\":" + SECURITY_CHECKLIST
                                + ",\"expectedRevision\":" + reviewRevision + "}"))
                .hasStatusOk();
        assertThat(JsonPath.<Integer>read(requestJson(requestId), "$.stage")).isEqualTo(3);

        MvcTestResult submitted = mvc.post()
                .uri("/api/security-requests/{id}/submissions", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .header("Idempotency-Key", "key-sec-only-" + requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":" + requestRevision(requestId) + "}")
                .exchange();
        assertThat(submitted).hasStatus(HttpStatus.CREATED);
        Long submissionId =
                ((Number) JsonPath.read(submitted.getResponse().getContentAsString(), "$.submissionId")).longValue();

        // INFRA 는 요구 분야가 아니라 최종 확인 불가(422), SECURITY 는 INFRA 선행 없이 바로 확인·확정
        assertThat(finalConfirm(INFRA, submissionId, requestId)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(finalConfirm(SECURITY, submissionId, requestId)).hasStatus(HttpStatus.CREATED);
        assertThat(mvc.put()
                        .uri("/api/submissions/{id}/confirmation", submissionId)
                        .with(authentication(SECURITY))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + requestRevision(requestId) + "}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CONFIRMED");
    }

    /** 관리자가 현재 초안에 SECURITY 만 요구하는 서류 유형을 추가해 게시한다(이미 있으면 건너뜀). */
    private void publishSecurityOnlyDocType() throws Exception {
        String current = mvc.get()
                .uri("/api/publications/current")
                .with(authentication(APPLICANT))
                .exchange()
                .getResponse()
                .getContentAsString();
        if (current.contains("\"SEC_ONLY\"")) {
            return;
        }
        String body = mvc.get()
                .uri("/api/configurations/current")
                .with(authentication(ADMIN))
                .exchange()
                .getResponse()
                .getContentAsString();
        Long configurationId = ((Number) JsonPath.read(body, "$.configurationId")).longValue();
        long revision = ((Number) JsonPath.read(body, "$.revision")).longValue();
        String draft = new ObjectMapper().writeValueAsString(JsonPath.read(body, "$.draft"));
        // 기존 유형 뒤에 덧붙인다(다른 테스트가 docTypes[0]==FIREWALL 을 기대)
        String modified = draft.replaceFirst(
                "\\],\"fields\":\\[",
                ",{\"code\":\"SEC_ONLY\",\"name\":\"보안 단독 검토 신청서(합성)\",\"description\":null,\"active\":true,"
                        + "\"templateKey\":\"sec-only-v1\",\"generatesFirewallRules\":false,\"reviewFields\":[\"SECURITY\"],"
                        + "\"sortOrder\":9,\"exportFormats\":[\"MARKDOWN\",\"PDF\"]}],\"fields\":[");
        assertThat(modified).contains("SEC_ONLY");
        MvcTestResult saved = mvc.put()
                .uri("/api/configurations/{id}", configurationId)
                .with(authentication(ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"draft\":" + modified + ",\"note\":\"SECURITY 단독 서류 추가(합성)\",\"expectedRevision\":"
                        + revision + "}")
                .exchange();
        assertThat(saved).hasStatusOk();
        long savedRevision =
                ((Number) JsonPath.read(saved.getResponse().getContentAsString(), "$.revision")).longValue();
        assertThat(mvc.post()
                        .uri("/api/configurations/{id}/publications", configurationId)
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + savedRevision + "}"))
                .hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult finalConfirm(Authentication who, Long submissionId, Long requestId) throws Exception {
        return mvc.post()
                .uri("/api/submissions/{id}/final-confirmations", submissionId)
                .with(authentication(who))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opinion\":\"확인(합성)\",\"expectedRevision\":" + requestRevision(requestId) + "}")
                .exchange();
    }

    private void answerCase(Long businessCaseId, String fieldKey, String value) {
        assertThat(mvc.put()
                        .uri("/api/business-cases/{id}/answers/{key}", businessCaseId, fieldKey)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value + "\"}"))
                .hasStatusOk();
    }

    private String requestJson(Long requestId) throws Exception {
        return mvc.get()
                .uri("/api/security-requests/{id}", requestId)
                .with(authentication(APPLICANT))
                .exchange()
                .getResponse()
                .getContentAsString();
    }

    private long requestRevision(Long requestId) throws Exception {
        return ((Number) JsonPath.read(requestJson(requestId), "$.revision")).longValue();
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
