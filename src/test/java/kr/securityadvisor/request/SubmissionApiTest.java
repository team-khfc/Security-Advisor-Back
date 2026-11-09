package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
@SpringBootTest(properties = {"securityadvisor.demo.account-password=Test-Demo-1234", "securityadvisor.llm.mode=fake"})
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

    @Autowired
    private kr.securityadvisor.request.service.FinalDraftService finalDraftService;

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

        // 제출 뒤 공통 정보 변경(같은 사업 건의 보안성검토 신청은 아직 1단계라 편집 가능) → 제출본은 불변, 담당자에게 알림(F-17)
        Long businessCaseId = ((Number) JsonPath.read(requestJson(requestId), "$.businessCaseId")).longValue();
        String caseJson = mvc.get()
                .uri("/api/business-cases/{id}", businessCaseId)
                .with(authentication(APPLICANT))
                .exchange()
                .getResponse()
                .getContentAsString();
        long periodRevision = ((Number)
                        ((List<?>) JsonPath.read(caseJson, "$.caseAnswers[?(@.fieldKey=='project.period')].revision"))
                                .get(0))
                .longValue();
        assertThat(mvc.put()
                        .uri("/api/business-cases/{id}/answers/{key}", businessCaseId, "project.period")
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"2027-01-01~2027-12-31\",\"expectedRevision\":" + periodRevision + "}"))
                .hasStatusOk();
        assertThat(mvc.get().uri("/api/submissions/{id}", submissionId).with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.snapshot.answers[?(@.fieldKey=='project.period')].value")
                .isEqualTo(List.of("2026-12-01~2027-11-30"));
        assertThat(mvc.get().uri("/api/notifications").with(authentication(INFRA)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath(
                        "$[?(@.kind=='COMMON_INFO_CHANGED' && @.link=='/api/security-requests/" + requestId + "')]")
                .asArray()
                .hasSize(1);
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
        assertThat(actions).contains("SUBMITTED", "FINAL_CONFIRMED", "REQUEST_CONFIRMED", "FIREWALL_RULES_CREATED");

        // F-15: FIREWALL 확정으로 규칙이 생성되고(적용 대기, 서류 확정 ≠ 적용), INFRA 만 적용 기록·회수를 남긴다
        MvcTestResult rules = mvc.get()
                .uri("/api/security-requests/{id}/firewall-rules", requestId)
                .with(authentication(APPLICANT))
                .exchange();
        assertThat(rules).hasStatusOk();
        assertThat(rules).bodyJson().extractingPath("$.length()").isEqualTo(1);
        assertThat(rules).bodyJson().extractingPath("$[0].status").isEqualTo("PENDING_APPLY");
        assertThat(rules).bodyJson().extractingPath("$[0].port").isEqualTo("443");
        assertThat(rules).bodyJson().extractingPath("$[0].endDate").isEqualTo("2027-11-30");
        Long ruleId =
                ((Number) JsonPath.read(rules.getResponse().getContentAsString(), "$[0].firewallRuleId")).longValue();
        String applied = "{\"result\":\"APPLIED\",\"note\":\"장비 반영(합성)\",\"expectedRevision\":0}";
        assertThat(mvc.put()
                        .uri("/api/firewall-rules/{id}/application", ruleId)
                        .with(authentication(SECURITY))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applied))
                .hasStatus(HttpStatus.FORBIDDEN);
        MvcTestResult appliedResult = mvc.put()
                .uri("/api/firewall-rules/{id}/application", ruleId)
                .with(authentication(INFRA))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(applied)
                .exchange();
        assertThat(appliedResult).hasStatusOk();
        assertThat(appliedResult).bodyJson().extractingPath("$.status").isEqualTo("APPLIED");
        assertThat(appliedResult).bodyJson().extractingPath("$.effectiveStatus").isEqualTo("APPLIED");

        // F-15 연장: 규칙은 그대로, 값을 미리 채운 새 방화벽 신청이 같은 사업 건에 1단계로 생긴다. 남의 규칙은 404, 중복 연장 422
        assertThat(mvc.post()
                        .uri("/api/firewall-rules/{id}/renewal-requests", ruleId)
                        .with(authentication(APPLICANT2))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPeriod\":\"2027-12-01~2028-11-30\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        MvcTestResult renewal = mvc.post()
                .uri("/api/firewall-rules/{id}/renewal-requests", ruleId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPeriod\":\"2027-12-01~2028-11-30\",\"note\":\"서비스 1년 연장(합성)\"}")
                .exchange();
        assertThat(renewal).hasStatus(HttpStatus.CREATED);
        assertThat(renewal).bodyJson().extractingPath("$.rule.status").isEqualTo("APPLIED");
        assertThat(renewal).bodyJson().extractingPath("$.rule.endDate").isEqualTo("2027-11-30");
        assertThat(renewal).bodyJson().extractingPath("$.request.stage").isEqualTo(1);
        assertThat(renewal).bodyJson().extractingPath("$.request.docType").isEqualTo("FIREWALL");
        assertThat(renewal)
                .bodyJson()
                .extractingPath("$.prefilledFieldKeys")
                .asArray()
                .contains("firewall.period", "firewall.protocol", "firewall.port", "firewall.purpose");
        Long renewalRequestId = ((Number)
                        JsonPath.read(renewal.getResponse().getContentAsString(), "$.request.securityRequestId"))
                .longValue();
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}", renewalRequestId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.answers[?(@.fieldKey=='firewall.period')].value")
                .isEqualTo(List.of("2027-12-01~2028-11-30"));
        assertThat(mvc.post()
                        .uri("/api/firewall-rules/{id}/renewal-requests", ruleId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPeriod\":\"2027-12-01~2028-11-30\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        long ruleRevision = ((Number) JsonPath.read(
                        mvc.get()
                                .uri("/api/security-requests/{id}/firewall-rules", requestId)
                                .with(authentication(INFRA))
                                .exchange()
                                .getResponse()
                                .getContentAsString(),
                        "$[0].revision"))
                .longValue();
        assertThat(mvc.put()
                        .uri("/api/firewall-rules/{id}/revocation", ruleId)
                        .with(authentication(INFRA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"서비스 종료(합성)\",\"expectedRevision\":" + ruleRevision + "}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("REVOKED");
        assertThat(mvc.get().uri("/api/firewall-rules?effectiveStatus=REVOKED").with(authentication(INFRA)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.firewallRuleId==" + ruleId + ")]")
                .asArray()
                .hasSize(1);
    }

    @Test
    @DisplayName("최종 초안 미확정·원본 변경은 제출 차단, 사람 수정·확인 후 스냅샷과 PDF 보존")
    void finalDraftAndPdf() throws Exception {
        Long requestId = createFirewallRequestAtStage3();
        answerAsApplicantNew(requestId, "firewall.purpose", "합성 최종 보고서 검증");
        MvcTestResult generated = mvc.post()
                .uri("/api/security-requests/{id}/final-draft-generations", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":" + requestRevision(requestId) + "}")
                .exchange();
        assertThat(generated).hasStatus(HttpStatus.CREATED);
        assertThat(generated).bodyJson().extractingPath("$.valueSource").isEqualTo("AI_INFERRED");
        Long draftId =
                ((Number) JsonPath.read(generated.getResponse().getContentAsString(), "$.finalDraftId")).longValue();
        MemberPrincipal applicant = (MemberPrincipal) APPLICANT.getPrincipal();
        String pendingGeneration = finalDraftService.prepare(applicant, requestId, requestRevision(requestId));
        assertThat(submit(requestId, "final-draft-unconfirmed")).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/final-draft", requestId)
                        .with(authentication(APPLICANT2)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.put()
                        .uri("/api/final-drafts/{id}/confirmation", draftId)
                        .with(authentication(INFRA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0,\"text\":\"무권한 확인\"}"))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.put()
                        .uri("/api/final-drafts/{id}/confirmation", draftId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0,\"text\":\"사용자가 확인한 합성 최종 서술\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.confirmed")
                .isEqualTo(true);
        // 모델 응답을 기다리는 동안 사용자가 확인한 본문을 늦은 AI 응답이 덮어쓸 수 없다.
        assertThatThrownBy(() -> finalDraftService.propose(
                        applicant,
                        requestId,
                        pendingGeneration,
                        "늦게 도착한 AI 응답",
                        kr.securityadvisor.common.schema.ValueSource.AI_INFERRED,
                        "fake/offline"))
                .isInstanceOf(kr.securityadvisor.common.error.ConflictException.class);
        // 별도 답변의 변경은 확인된 초안을 다시 사용할 수 없게 한다.
        long answerRevision = ((Number) ((List<?>) JsonPath.read(
                                requestJson(requestId), "$.answers[?(@.fieldKey=='firewall.purpose')].revision"))
                        .get(0))
                .longValue();
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/answers/firewall.purpose", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"변경한 합성 목적\",\"expectedRevision\":" + answerRevision + "}"))
                .hasStatusOk();
        assertThat(submit(requestId, "final-draft-stale")).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/final-draft", requestId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.stale")
                .isEqualTo(true);
        MvcTestResult replacement = mvc.post()
                .uri("/api/security-requests/{id}/final-drafts", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":" + requestRevision(requestId) + ",\"text\":\"변경을 반영한 합성 최종 서술\"}")
                .exchange();
        assertThat(replacement).hasStatus(HttpStatus.CREATED);
        Long newId =
                ((Number) JsonPath.read(replacement.getResponse().getContentAsString(), "$.finalDraftId")).longValue();
        assertThat(mvc.put()
                        .uri("/api/final-drafts/{id}/confirmation", draftId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1,\"text\":\"이전 초안\"}"))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.put()
                        .uri("/api/final-drafts/{id}/confirmation", newId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0,\"text\":\"변경을 반영한 합성 최종 서술\"}"))
                .hasStatusOk();
        MvcTestResult submitted = submit(requestId, "final-draft-confirmed");
        assertThat(submitted).hasStatus(HttpStatus.CREATED);
        assertThat(submitted)
                .bodyJson()
                .extractingPath("$.snapshot.finalDraft.text")
                .isEqualTo("변경을 반영한 합성 최종 서술");
        Long submissionId =
                ((Number) JsonPath.read(submitted.getResponse().getContentAsString(), "$.submissionId")).longValue();
        assertThat(submit(requestId, "final-draft-confirmed")).hasStatusOk();
        assertThat(mvc.put()
                        .uri("/api/final-drafts/{id}/confirmation", newId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1,\"text\":\"제출 이후 변경 시도\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        MvcTestResult doc = mvc.post()
                .uri("/api/security-requests/{id}/generated-documents", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"submissionId\":" + submissionId + "}")
                .exchange();
        assertThat(doc).hasStatus(HttpStatus.CREATED);
        Long documentId =
                ((Number) JsonPath.read(doc.getResponse().getContentAsString(), "$.generatedDocumentId")).longValue();
        MvcTestResult pdf = mvc.get()
                .uri("/api/generated-documents/{id}/download?format=PDF", documentId)
                .with(authentication(APPLICANT))
                .exchange();
        assertThat(pdf).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_PDF);
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.getResponse().getContentAsByteArray())) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertThat(text).contains("변경을 반영한 합성 최종 서술").contains("사용자 확인");
        }
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
                .content("{\"title\":\"제출 테스트\",\"summary\":\"개요\",\"department\":\"정보화부\","
                        + "\"docTypes\":[\"FIREWALL\",\"SECURITY_REVIEW\"]}")
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
