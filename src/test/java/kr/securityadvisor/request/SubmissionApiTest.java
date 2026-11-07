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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * F-07: 3단계 → 제출(제출 전 검사·멱등성 키·스냅샷) → 4단계 INFRA 최종 확인 → SECURITY 최종 확인 → SECURITY 확정 → 종료, 문서함.
 */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SubmissionApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication APPLICANT2 = auth("applicant2", "이신청", MemberRole.APPLICANT);
    private static final Authentication INFRA = auth("infra", "박인프라", MemberRole.INFRA);
    private static final Authentication SECURITY = auth("security", "정보안", MemberRole.SECURITY);

    private static final String INFRA_CHECKLIST =
            "{\"source\":true,\"destination\":true,\"protocol\":true,\"port\":true,\"period\":true}";
    private static final String SECURITY_CHECKLIST =
            "{\"processed_info\":true,\"external_transfer\":true,\"access_control\":true,\"logging\":true}";

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("제출 전 검사 → 제출(멱등) → INFRA·SECURITY 최종 확인(INFRA 선행) → SECURITY 확정 → 종료 상태, 문서함")
    void submitAndConfirm() throws Exception {
        Long requestId = createFirewallRequestAtStage3();

        // Idempotency-Key 없으면 400, 필수 항목(firewall.purpose) 누락이면 422
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/submissions", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + requestRevision(requestId) + "}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        MvcTestResult missing = submit(requestId, "key-synthetic-1");
        assertThat(missing).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(missing).bodyJson().extractingPath("$.detail").asString().contains("firewall.purpose");

        // 3단계에서 부족한 내용을 보충한 뒤 제출 → 201, 같은 키 재요청 → 200 같은 제출본, 다른 키 → 4단계라 422
        answerAsApplicantNew(requestId, "firewall.purpose", "외부 API 연동(합성 시나리오)");
        MvcTestResult submitted = submit(requestId, "key-synthetic-1");
        assertThat(submitted).hasStatus(HttpStatus.CREATED);
        assertThat(submitted).headers().containsHeader("Location");
        Long submissionId =
                ((Number) JsonPath.read(submitted.getResponse().getContentAsString(), "$.submissionId")).longValue();
        assertThat(submitted).bodyJson().extractingPath("$.sequenceNo").isEqualTo(1);
        assertThat(submitted).bodyJson().extractingPath("$.snapshot.docType").isEqualTo("FIREWALL");
        assertThat(submitted)
                .bodyJson()
                .extractingPath("$.snapshot.answers[?(@.fieldKey=='network.source')].valueSource")
                .isEqualTo(List.of("STAFF_INPUT"));
        assertThat(requestStage(requestId)).isEqualTo(4);
        assertThat(requestStatus(requestId)).isEqualTo("SUBMITTED");
        MvcTestResult again = submit(requestId, "key-synthetic-1");
        assertThat(again).hasStatusOk();
        assertThat(((Number) JsonPath.read(again.getResponse().getContentAsString(), "$.submissionId")).longValue())
                .isEqualTo(submissionId);
        assertThat(submit(requestId, "key-synthetic-2")).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/deadlines", requestId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.kind=='FINAL_CONFIRMATION' && @.active==true)]")
                .asArray()
                .hasSize(1);

        // 문서함: 신청자 본인에게는 보이고 다른 신청자에게는 안 보인다. 담당자는 전체
        assertThat(mvc.get().uri("/api/submissions?latestOnly=true").with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.submissionId==" + submissionId + ")].docType")
                .isEqualTo(List.of("FIREWALL"));
        assertThat(mvc.get().uri("/api/submissions").with(authentication(APPLICANT2)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.submissionId==" + submissionId + ")]")
                .asArray()
                .isEmpty();
        assertThat(mvc.get().uri("/api/submissions?docType=FIREWALL").with(authentication(SECURITY)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.submissionId==" + submissionId + ")].caseTitle")
                .isEqualTo(List.of("제출 테스트"));
        assertThat(mvc.get().uri("/api/submissions/{id}", submissionId).with(authentication(APPLICANT2)))
                .hasStatus(HttpStatus.NOT_FOUND);

        // 최종 확인: 신청자 403, SECURITY 가 먼저 하면 422(INFRA 선행), INFRA 201, INFRA 중복 422, 확정은 두 분야 전 422
        assertThat(finalConfirm(APPLICANT, submissionId, requestId)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(finalConfirm(SECURITY, submissionId, requestId)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        MvcTestResult infraConfirmed = finalConfirm(INFRA, submissionId, requestId);
        assertThat(infraConfirmed).hasStatus(HttpStatus.CREATED);
        assertThat(infraConfirmed).bodyJson().extractingPath("$.field").isEqualTo("INFRA");
        assertThat(finalConfirm(INFRA, submissionId, requestId)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(confirm(SECURITY, submissionId, requestId)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(finalConfirm(SECURITY, submissionId, requestId)).hasStatus(HttpStatus.CREATED);
        assertThat(mvc.get()
                        .uri("/api/submissions/{id}/final-confirmations", submissionId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].field")
                .isEqualTo(List.of("INFRA", "SECURITY"));

        // 확정: INFRA 는 403(배정된 SECURITY 만), SECURITY 는 200 → CONFIRMED(종료), 최종 확정 기한 종료, 이후 반환 422
        assertThat(confirm(INFRA, submissionId, requestId)).hasStatus(HttpStatus.FORBIDDEN);
        MvcTestResult confirmed = confirm(SECURITY, submissionId, requestId);
        assertThat(confirmed).hasStatusOk();
        assertThat(confirmed).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(requestStatus(requestId)).isEqualTo("CONFIRMED");
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/deadlines", requestId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.active==true)]")
                .asArray()
                .isEmpty();
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/stage-returns", requestId)
                        .with(authentication(SECURITY))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStage\":3,\"reason\":\"확정 후 반환 시도\",\"expectedRevision\":"
                                + requestRevision(requestId) + "}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        MvcTestResult trail = mvc.get()
                .uri("/api/security-requests/{id}/audit-events", requestId)
                .with(authentication(APPLICANT))
                .exchange();
        List<String> actions = JsonPath.read(trail.getResponse().getContentAsString(), "$[*].action");
        assertThat(actions).contains("SUBMITTED", "FINAL_CONFIRMED", "REQUEST_CONFIRMED");
    }

    private MvcTestResult submit(Long requestId, String key) throws Exception {
        return mvc.post()
                .uri("/api/security-requests/{id}/submissions", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":" + requestRevision(requestId) + "}")
                .exchange();
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

    private MvcTestResult confirm(Authentication who, Long submissionId, Long requestId) throws Exception {
        return mvc.put()
                .uri("/api/submissions/{id}/confirmation", submissionId)
                .with(authentication(who))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":" + requestRevision(requestId) + "}")
                .exchange();
    }

    /** 사업 건 생성 → 사용자 답변(firewall.purpose 만 비워 둠) → 협의 요청 → INFRA 할당 입력·완료·협의 완료 → SECURITY 완료 → 3단계. */
    private Long createFirewallRequestAtStage3() throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"제출 테스트\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String body = created.getResponse().getContentAsString();
        Long businessCaseId = ((Number) JsonPath.read(body, "$.businessCaseId")).longValue();
        Long requestId = ((Number) JsonPath.read(body, "$.requests[0].securityRequestId")).longValue();
        answerCaseNew(businessCaseId, "project.period", "2026-12-01~2027-11-30");
        answerCaseNew(businessCaseId, "data.personal_info", "false");
        answerCaseNew(businessCaseId, "system.external_transfer", "true");
        answerAsApplicantNew(requestId, "firewall.protocol", "TCP");
        answerAsApplicantNew(requestId, "firewall.port", "443");
        answerAsApplicantNew(requestId, "firewall.period", "2026-12-01~2027-11-30");

        MvcTestResult requested = mvc.post()
                .uri("/api/security-requests/{id}/reviews", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":" + requestRevision(requestId) + "}")
                .exchange();
        assertThat(requested).hasStatus(HttpStatus.CREATED);
        String reviews = requested.getResponse().getContentAsString();
        Long infraReviewId = reviewId(reviews, "INFRA");
        Long securityReviewId = reviewId(reviews, "SECURITY");
        staffInput(infraReviewId, "system.target_server", "합성 업무 서버 01");
        staffInput(infraReviewId, "network.source", "10.0.0.0/24 (합성)");
        staffInput(infraReviewId, "network.destination", "10.0.1.10 (합성)");
        assertThat(mvc.put()
                        .uri("/api/reviews/{id}/allocation-completion", infraReviewId)
                        .with(authentication(INFRA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + reviewRevision(infraReviewId) + "}"))
                .hasStatusOk();
        assertThat(complete(INFRA, infraReviewId, INFRA_CHECKLIST)).hasStatusOk();
        assertThat(complete(SECURITY, securityReviewId, SECURITY_CHECKLIST)).hasStatusOk();
        assertThat(requestStage(requestId)).isEqualTo(3);
        return requestId;
    }

    private void staffInput(Long reviewId, String fieldKey, String value) {
        assertThat(mvc.put()
                        .uri("/api/reviews/{id}/answers/{key}", reviewId, fieldKey)
                        .with(authentication(INFRA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value + "\"}"))
                .hasStatusOk();
    }

    private MvcTestResult complete(Authentication who, Long reviewId, String checklist) throws Exception {
        return mvc.put()
                .uri("/api/reviews/{id}/completion", reviewId)
                .with(authentication(who))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opinion\":\"검토 의견(합성)\",\"checklist\":" + checklist + ",\"expectedRevision\":"
                        + reviewRevision(reviewId) + "}")
                .exchange();
    }

    private void answerCaseNew(Long businessCaseId, String fieldKey, String value) {
        assertThat(mvc.put()
                        .uri("/api/business-cases/{id}/answers/{key}", businessCaseId, fieldKey)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value + "\"}"))
                .hasStatusOk();
    }

    private void answerAsApplicantNew(Long requestId, String fieldKey, String value) {
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/answers/{key}", requestId, fieldKey)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value + "\"}"))
                .hasStatusOk();
    }

    private String requestJson(Long requestId) throws Exception {
        MvcTestResult result = mvc.get()
                .uri("/api/security-requests/{id}", requestId)
                .with(authentication(APPLICANT))
                .exchange();
        assertThat(result).hasStatusOk();
        return result.getResponse().getContentAsString();
    }

    private long requestRevision(Long requestId) throws Exception {
        return ((Number) JsonPath.read(requestJson(requestId), "$.revision")).longValue();
    }

    private int requestStage(Long requestId) throws Exception {
        return JsonPath.read(requestJson(requestId), "$.stage");
    }

    private String requestStatus(Long requestId) throws Exception {
        return JsonPath.read(requestJson(requestId), "$.status");
    }

    private long reviewRevision(Long reviewId) throws Exception {
        MvcTestResult result = mvc.get()
                .uri("/api/reviews/{id}", reviewId)
                .with(authentication(INFRA))
                .exchange();
        assertThat(result).hasStatusOk();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.revision")).longValue();
    }

    private static Long reviewId(String body, String field) {
        List<Number> ids = JsonPath.read(body, "$.reviews[?(@.field=='" + field + "')].reviewId");
        return ids.get(0).longValue();
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
