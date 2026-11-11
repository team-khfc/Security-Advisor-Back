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

/**
 * F-04 담당자 검토 + F-05 보완·반환·종결의 시연 경로를 실제 DB 로 끝까지 따라간다:
 * 협의 요청 → INFRA 할당 입력·할당 완료 → SECURITY 보완 요청 → 신청자 답변 → SECURITY 완료 실패(INFRA 미완)
 * → INFRA 완료 → SECURITY 완료 → 3단계 → 차수 반환 3→1 → 종결 → 이후 422.
 */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = {"securityadvisor.demo.account-password=Test-Demo-1234", "securityadvisor.llm.mode=fake"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReviewWorkflowApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication APPLICANT2 = auth("applicant2", "이신청", MemberRole.APPLICANT);
    private static final Authentication INFRA = auth("infra", "박인프라", MemberRole.INFRA);
    private static final Authentication SECURITY = auth("security", "정보안", MemberRole.SECURITY);
    private static final Authentication ADMIN = auth("admin", "최관리", MemberRole.ADMIN);

    private static final String INFRA_CHECKLIST =
            "{\"source\":true,\"destination\":true,\"protocol\":true,\"port\":true,\"period\":true}";
    private static final String SECURITY_CHECKLIST =
            "{\"processed_info\":true,\"external_transfer\":true,\"access_control\":true,\"logging\":true}";

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("담당자 검토 → 보완 → 두 분야 협의 완료 → 3단계 → 차수 반환 → 종결")
    void fullWorkflow() throws Exception {
        Long requestId = createFirewallRequest();
        MvcTestResult requested = mvc.post()
                .uri("/api/security-requests/{id}/reviews", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0}")
                .exchange();
        assertThat(requested).hasStatus(HttpStatus.CREATED);
        String body = requested.getResponse().getContentAsString();
        Long infraReviewId = reviewId(body, "INFRA");
        Long securityReviewId = reviewId(body, "SECURITY");

        // 요청함: infra 에게 INFRA 협의가, security 에게 SECURITY 협의가 보이고 신청자에게는 비어 있다
        assertThat(mvc.get().uri("/api/reviews").with(authentication(INFRA)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.request.securityRequestId==" + requestId + ")].review.field")
                .isEqualTo(List.of("INFRA"));
        assertThat(mvc.get().uri("/api/reviews").with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.length()")
                .isEqualTo(0);

        // 담당자 입력: 다른 분야 담당자는 403, 배정된 INFRA 는 공통 항목에 STAFF_INPUT
        String allocation = "{\"value\":\"10.0.0.0/24 (합성 데이터)\"}";
        assertThat(mvc.put()
                        .uri("/api/reviews/{id}/answers/network.source", infraReviewId)
                        .with(authentication(SECURITY))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocation))
                .hasStatus(HttpStatus.FORBIDDEN);
        MvcTestResult input = mvc.put()
                .uri("/api/reviews/{id}/answers/network.source", infraReviewId)
                .with(authentication(INFRA))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(allocation)
                .exchange();
        assertThat(input).hasStatusOk();
        assertThat(input).bodyJson().extractingPath("$.valueSource").isEqualTo("STAFF_INPUT");
        assertThat(input).bodyJson().extractingPath("$.scope").isEqualTo("CASE");

        // 담당자 확인: 신청자가 적은 포트를 INFRA 가 확인 → STAFF_CONFIRMED
        long portRevision = answerRevision(requestId, "firewall.port");
        MvcTestResult confirmed = mvc.put()
                .uri("/api/reviews/{id}/answers/firewall.port/confirmation", infraReviewId)
                .with(authentication(INFRA))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":" + portRevision + "}")
                .exchange();
        assertThat(confirmed).hasStatusOk();
        assertThat(confirmed).bodyJson().extractingPath("$.valueSource").isEqualTo("STAFF_CONFIRMED");
        assertThat(confirmed).bodyJson().extractingPath("$.confirmedBy").isEqualTo("infra");

        // SECURITY 완료는 INFRA 할당 전이라 422
        assertThat(complete(SECURITY, securityReviewId, SECURITY_CHECKLIST)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);

        // INFRA 할당 완료(협의 완료 아님)
        MvcTestResult allocated = mvc.put()
                .uri("/api/reviews/{id}/allocation-completion", infraReviewId)
                .with(authentication(INFRA))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":" + reviewRevision(infraReviewId) + "}")
                .exchange();
        assertThat(allocated).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(requestStatus(requestId)).isEqualTo("IN_REVIEW");

        // SECURITY 보완 요청 → 신청 상태 SUPPLEMENT_REQUESTED, 대상 항목 needsReview, 보완 응답 기한 생성
        MvcTestResult supplement = mvc.post()
                .uri("/api/reviews/{id}/supplements", securityReviewId)
                .with(authentication(SECURITY))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"fieldKeys\":[\"firewall.purpose\"],\"reason\":\"허용 목적을 구체적으로 적어 주세요\",\"expectedRevision\":"
                                + reviewRevision(securityReviewId) + "}")
                .exchange();
        assertThat(supplement).hasStatus(HttpStatus.CREATED);
        assertThat(supplement).headers().containsHeader("Location");
        Long supplementId =
                ((Number) JsonPath.read(supplement.getResponse().getContentAsString(), "$.supplementId")).longValue();
        assertThat(requestStatus(requestId)).isEqualTo("SUPPLEMENT_REQUESTED");
        assertThat(answerNeedsReview(requestId, "firewall.purpose")).isTrue();
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/deadlines", requestId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.kind=='SUPPLEMENT_RESPONSE' && @.active==true)]")
                .asArray()
                .hasSize(1);
        // 보완 대기 중 SECURITY 협의 기한은 정지(종료 + 남은 영업일 보관), INFRA 기한은 그대로(F-13)
        MvcTestResult pausedDeadlines = mvc.get()
                .uri("/api/security-requests/{id}/deadlines", requestId)
                .with(authentication(APPLICANT))
                .exchange();
        assertThat(pausedDeadlines)
                .bodyJson()
                .extractingPath("$[?(@.kind=='REVIEW' && @.field=='SECURITY' && @.active==true)]")
                .asArray()
                .isEmpty();
        assertThat(pausedDeadlines)
                .bodyJson()
                .extractingPath("$[?(@.kind=='REVIEW' && @.field=='SECURITY' && @.paused==true)].remainingBusinessDays")
                .asArray()
                .hasSize(1);
        assertThat(pausedDeadlines)
                .bodyJson()
                .extractingPath("$[?(@.kind=='REVIEW' && @.field=='INFRA' && @.active==true)]")
                .asArray()
                .hasSize(1);
        // 보완 중에는 SECURITY 완료 불가
        assertThat(complete(SECURITY, securityReviewId, SECURITY_CHECKLIST)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);

        // 신청자가 값을 고치고(needsReview 해제) 보완 답변 — 다른 신청자는 404
        answerAsApplicant(requestId, "firewall.purpose", "외부 API 연동(합성 시나리오)");
        assertThat(answerNeedsReview(requestId, "firewall.purpose")).isFalse();
        assertThat(mvc.post()
                        .uri("/api/supplements/{id}/answers", supplementId)
                        .with(authentication(APPLICANT2))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"수정했습니다\",\"expectedRevision\":0}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        MvcTestResult answered = mvc.post()
                .uri("/api/supplements/{id}/answers", supplementId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"수정했습니다\",\"expectedRevision\":0}")
                .exchange();
        assertThat(answered).hasStatus(HttpStatus.CREATED);
        assertThat(answered).bodyJson().extractingPath("$.status").isEqualTo("ANSWERED");
        assertThat(requestStatus(requestId)).isEqualTo("IN_REVIEW");
        // 담당자 명시적 해소(PUT …/resolution): 답변된 보완만, 그 분야 배정자만, 멱등
        long supplementRevision = ((Number) ((List<?>) JsonPath.read(
                                mvc.get()
                                        .uri("/api/security-requests/{id}/supplements", requestId)
                                        .with(authentication(SECURITY))
                                        .exchange()
                                        .getResponse()
                                        .getContentAsString(),
                                "$[?(@.supplementId==" + supplementId + ")].revision"))
                        .get(0))
                .longValue();
        assertThat(mvc.put()
                        .uri("/api/supplements/{id}/resolution", supplementId)
                        .with(authentication(INFRA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"확인\",\"expectedRevision\":" + supplementRevision + "}"))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.put()
                        .uri("/api/supplements/{id}/resolution", supplementId)
                        .with(authentication(SECURITY))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"확인\",\"expectedRevision\":" + supplementRevision + "}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("RESOLVED");
        // 답변 후 SECURITY 협의 기한이 남은 영업일로 재개된다
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/deadlines", requestId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.kind=='REVIEW' && @.field=='SECURITY' && @.active==true)]")
                .asArray()
                .hasSize(1);

        // INFRA 완료(체크리스트 누락은 422) → 2단계에서 사용자가 답변을 바꾸면 완료된 INFRA 협의는 재검토(RECHECK)로(불변식 7)
        assertThat(complete(INFRA, infraReviewId, "{\"source\":true}")).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(complete(INFRA, infraReviewId, INFRA_CHECKLIST)).hasStatusOk();
        assertThat(requestStatus(requestId)).isEqualTo("IN_REVIEW");
        answerAsApplicant(requestId, "firewall.purpose", "목적 다시 수정");
        assertThat(mvc.get().uri("/api/reviews/{id}", infraReviewId).with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("RECHECK");
        // SECURITY 완료만으로는 전이하지 않고(INFRA 재검토 중), INFRA 가 다시 완료해야 3단계 최종 검토 + 협의 기한 종료 + 보완 RESOLVED
        assertThat(complete(SECURITY, securityReviewId, SECURITY_CHECKLIST)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(requestStage(requestId)).isEqualTo(2);
        MvcTestResult infraDone = complete(INFRA, infraReviewId, INFRA_CHECKLIST);
        assertThat(infraDone).hasStatusOk();
        assertThat(infraDone).bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");
        assertThat(infraDone).bodyJson().extractingPath("$.checklist.port").isEqualTo(true);
        assertThat(complete(SECURITY, securityReviewId, SECURITY_CHECKLIST)).hasStatusOk();
        assertThat(requestStage(requestId)).isEqualTo(3);
        assertThat(requestStatus(requestId)).isEqualTo("FINAL_REVIEW");
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/deadlines", requestId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.kind=='REVIEW' && @.active==true)]")
                .asArray()
                .hasSize(0);
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/supplements", requestId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].status")
                .isEqualTo("RESOLVED");

        // 3단계 최종 검토의 보충 답변은 협의를 되돌리지 않는다(담당자는 4단계 최종 확인에서 검증, 부족하면 4→3 반환)
        answerAsApplicant(requestId, "firewall.purpose", "3단계 보충 수정");
        assertThat(mvc.get().uri("/api/reviews/{id}", securityReviewId).with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("COMPLETED");

        // 차수 반환 3→1: 신청자는 403, 사유 없으면 400, 담당자+사유는 201, 지정 항목 강조, 1단계 작성 중
        // 항목별 사유(fieldReasons)의 키는 fieldKeys 에 없어도 지정 항목에 포함된다
        String returnBody = "{\"targetStage\":1,\"reason\":\"허용 기간 재확인 필요\",\"fieldKeys\":[\"firewall.period\"],"
                + "\"fieldReasons\":{\"firewall.period\":\"종료일을 사업 기간과 맞추세요\",\"firewall.purpose\":\"연동 대상 시스템을 적으세요\"},"
                + "\"expectedRevision\":" + requestRevision(requestId) + "}";
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/stage-returns", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/stage-returns", requestId)
                        .with(authentication(SECURITY))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStage\":1,\"reason\":\" \",\"expectedRevision\":"
                                + requestRevision(requestId) + "}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        MvcTestResult returned = mvc.post()
                .uri("/api/security-requests/{id}/stage-returns", requestId)
                .with(authentication(SECURITY))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(returnBody)
                .exchange();
        assertThat(returned).hasStatus(HttpStatus.CREATED);
        assertThat(returned).bodyJson().extractingPath("$.fromStage").isEqualTo(3);
        assertThat(returned).bodyJson().extractingPath("$.toStage").isEqualTo(1);
        assertThat(requestStage(requestId)).isEqualTo(1);
        assertThat(requestStatus(requestId)).isEqualTo("DRAFTING");
        assertThat(answerNeedsReview(requestId, "firewall.period")).isTrue();
        assertThat(answerNeedsReview(requestId, "firewall.purpose")).isTrue();
        assertThat(returned)
                .bodyJson()
                .extractingPath("$.fieldKeys")
                .asArray()
                .containsExactlyInAnyOrder("firewall.period", "firewall.purpose");
        assertThat(returned)
                .bodyJson()
                .extractingPath("$.fieldReasons['firewall.purpose']")
                .isEqualTo("연동 대상 시스템을 적으세요");
        MvcTestResult returns = mvc.get()
                .uri("/api/security-requests/{id}/stage-returns", requestId)
                .with(authentication(APPLICANT))
                .exchange();
        assertThat(returns).hasStatusOk();
        assertThat(returns).bodyJson().extractingPath("$.length()").isEqualTo(1);
        assertThat(returns)
                .bodyJson()
                .extractingPath("$[0].fieldReasons['firewall.period']")
                .isEqualTo("종료일을 사업 기간과 맞추세요");

        // 종결: 사유 필수, 이후 어떤 전이도 422. 감사 이력에 전 과정이 남는다
        MvcTestResult closed = mvc.put()
                .uri("/api/security-requests/{id}/closure", requestId)
                .with(authentication(SECURITY))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"사업 취소\",\"expectedRevision\":" + requestRevision(requestId) + "}")
                .exchange();
        assertThat(closed).hasStatusOk();
        assertThat(closed).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/reviews", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + requestRevision(requestId) + "}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        MvcTestResult trail = mvc.get()
                .uri("/api/security-requests/{id}/audit-events", requestId)
                .with(authentication(APPLICANT))
                .exchange();
        List<String> actions = JsonPath.read(trail.getResponse().getContentAsString(), "$[*].action");
        assertThat(actions)
                .contains(
                        "ANSWER_STAFF_CONFIRMED",
                        "SUPPLEMENT_REQUESTED",
                        "SUPPLEMENT_ANSWERED",
                        "REVIEW_COMPLETED",
                        "STAGE_ADVANCED",
                        "REVIEW_RECHECK",
                        "STAGE_RETURNED",
                        "REQUEST_CLOSED");
        // 공통(scope=CASE) 항목의 담당자 입력은 사업 건을 대상으로 기록된다(docs/ARCHITECTURE.md 5장 #6) — 관리자 전체 조회로 확인
        Long businessCaseId = ((Number) JsonPath.read(requestJson(requestId), "$.businessCaseId")).longValue();
        assertThat(mvc.get()
                        .uri("/api/audit-events?targetType=BUSINESS_CASE&targetId={id}&size=100", businessCaseId)
                        .with(authentication(ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.content[?(@.action=='ANSWER_STAFF_INPUT')]")
                .asArray()
                .hasSize(1);
    }

    @Autowired
    private kr.securityadvisor.request.service.AnswerService testAnswers;

    @Autowired
    private kr.securityadvisor.request.service.RequestAccessPolicy testAccess;

    @Autowired
    private tools.jackson.databind.ObjectMapper testJson;

    @Test
    void infrastructureConnectionsSaveAndGuard() throws Exception {
        Long id = createFirewallRequest();
        var requested = mvc.post()
                .uri("/api/security-requests/{id}/reviews", id)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0}")
                .exchange();
        assertThat(requested).hasStatus(HttpStatus.CREATED);
        var actor = new MemberPrincipal("infra", "박인프라", MemberRole.INFRA);
        var request = testAccess.viewableRequest(actor, id);
        var revisions = new java.util.LinkedHashMap<String, Long>();
        testAnswers.allAnswers(request).forEach(a -> revisions.put(a.fieldKey(), a.revision()));
        var one = new kr.securityadvisor.request.dto.InfrastructurePlanCommand.Connection(
                "row1",
                "합성 웹",
                "192.0.2.10",
                "DMZ",
                "합성 API",
                "198.51.100.20",
                "내부망",
                "TCP",
                "443",
                "API 조회",
                "아웃바운드",
                "2026-11-11",
                "2027-11-11",
                null,
                null);
        var two = new kr.securityadvisor.request.dto.InfrastructurePlanCommand.Connection(
                "row2",
                "합성 API",
                "198.51.100.20",
                "내부망",
                "합성 DB",
                "203.0.113.30",
                "내부망",
                "TCP",
                "5432",
                "DB 조회",
                "아웃바운드",
                "2026-11-11",
                "2027-11-11",
                null,
                null);
        var command = new kr.securityadvisor.request.dto.InfrastructurePlanCommand(
                0L,
                List.of(one, two),
                "합성 서버·망 분리 검토",
                revisions,
                1,
                false,
                false,
                List.of(),
                java.util.Map.of(),
                java.util.Map.of());
        String body = testJson.writeValueAsString(command);
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/infrastructure-plan", id)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/infrastructure-plan", id)
                        .with(authentication(INFRA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("192.0.2.10", "999.0.0.1")))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        var saved = mvc.put()
                .uri("/api/security-requests/{id}/infrastructure-plan", id)
                .with(authentication(SECURITY))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        assertThat(saved).hasStatusOk();
        assertThat(saved).bodyJson().extractingPath("$.connections.length()").isEqualTo(2);
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/infrastructure-plan", id)
                        .with(authentication(INFRA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/infrastructure-plan", id)
                        .with(authentication(SECURITY)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.connections[1].port")
                .isEqualTo("5432");
        var document = mvc.post()
                .uri("/api/security-requests/{id}/generated-documents", id)
                .with(authentication(INFRA))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aiSupplement\":false}")
                .exchange();
        assertThat(document).hasStatus(HttpStatus.CREATED);
        assertThat(document.getResponse().getContentAsString()).contains("합성 DB", "5432", "통신 구간 2");
        var reviews = requested.getResponse().getContentAsString();
        assertThat(complete(INFRA, reviewId(reviews, "INFRA"), INFRA_CHECKLIST)).hasStatusOk();
        assertThat(complete(SECURITY, reviewId(reviews, "SECURITY"), SECURITY_CHECKLIST))
                .hasStatusOk();
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/infrastructure-plan", id)
                        .with(authentication(INFRA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"expectedRevision\":0", "\"expectedRevision\":1")))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("망 구성 AI 제안은 배정된 검토 담당자만 요청하고 기존 저장값을 변경하지 않는다")
    void infrastructureProposalAccess() throws Exception {
        Long id = createFirewallRequest();
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/reviews", id)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .hasStatus(HttpStatus.CREATED);
        String body = "{\"expectedRevision\":0,\"explanation\":\"합성 자료에는 아직 IP 정보가 없습니다.\"}";
        for (var actor : List.of(APPLICANT, ADMIN))
            assertThat(mvc.post()
                            .uri("/api/security-requests/{id}/infrastructure-proposals", id)
                            .with(authentication(actor))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/infrastructure-proposals", id)
                        .with(authentication(SECURITY))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.connections.length()")
                .isEqualTo(0);
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/infrastructure-plan", id)
                        .with(authentication(SECURITY)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.revision")
                .isEqualTo(0);
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/infrastructure-proposals", id)
                        .with(authentication(SECURITY))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"expectedRevision\":0", "\"expectedRevision\":9")))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"FIREWALL", "SECURITY_REVIEW"})
    @DisplayName("서버 연결·미확인 AI 사양 임시 저장 후 확인 저장해야 협의할 수 있고 문서에 반영된다")
    void infrastructureServerDraftAndConfirmation(String docType) throws Exception {
        Long id = createReviewRequest(docType);
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/consultation-comparison", id)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.baselineAvailable")
                .isEqualTo(false);
        var reviews = mvc.post()
                .uri("/api/security-requests/{id}/reviews", id)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0}")
                .exchange();
        assertThat(reviews).hasStatus(HttpStatus.CREATED);
        var reviewer = new MemberPrincipal("infra", "박인프라", MemberRole.INFRA);
        var revisions = new java.util.LinkedHashMap<String, Long>();
        testAnswers
                .allAnswers(testAccess.viewableRequest(reviewer, id))
                .forEach(a -> revisions.put(a.fieldKey(), a.revision()));
        var body = testJson.readTree("""
                {"expectedRevision":0,"schemaVersion":2,"draft":true,"aiPending":false,"notes":"합성 운영 검토","answerRevisions":{},
                 "servers":[{"id":"web","name":"합성 웹","purpose":"업무 서비스","environment":"가상","cpu":"4 vCPU","memory":"8 GB","storage":"100 GB","os":"Linux","database":"미해당","address":"192.0.2.10","zone":"DMZ","aiInferred":true,"aiConfirmed":false,"evidence":{"cpu":"합성 첨부: 4 vCPU"}},
                 {"id":"api","name":"합성 API","purpose":"API 처리","environment":"가상","cpu":"8 vCPU","memory":"16 GB","storage":"200 GB","os":"Linux","database":"미해당","address":"198.51.100.20","zone":"내부망","aiInferred":false,"aiConfirmed":false,"evidence":{}}],
                 "connections":[{"id":"link","sourceSystem":"","sourceAddress":"","sourceZone":"","destinationSystem":"","destinationAddress":"","destinationZone":"","protocol":"TCP","port":"","purpose":"API 연결","direction":"아웃바운드","startDate":"2026-11-11","endDate":"2027-11-11","sourceServerId":"web","destinationServerId":"api"}],"operations":{},"checklist":{}}
                """);
        var command = (tools.jackson.databind.node.ObjectNode) body;
        command.set("answerRevisions", testJson.valueToTree(revisions));
        assertThat(saveInfrastructure(id, command))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.connections[0].sourceAddress")
                .isEqualTo("192.0.2.10");
        if (docType.equals("FIREWALL")) assertThat(requestJson(id)).contains("443");
        Long review = reviewId(reviews.getResponse().getContentAsString(), "INFRA");
        assertThat(complete(INFRA, review, INFRA_CHECKLIST)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        command.put("expectedRevision", 1);
        command.put("draft", false);
        assertThat(saveInfrastructure(id, command)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        ((tools.jackson.databind.node.ObjectNode) command.path("servers").get(0)).put("aiConfirmed", true);
        ((tools.jackson.databind.node.ObjectNode) command.path("connections").get(0)).put("port", "8443");
        command.set(
                "operations",
                testJson.valueToTree(java.util.Map.of(
                        "network", "DMZ와 내부망 분리", "management", "합성 VPN 관리 접속", "owner", "합성 운영팀", "backup", "매일 백업")));
        command.set(
                "checklist",
                testJson.valueToTree(java.util.Map.of(
                        "serverEnvironment", true, "network", true, "management", true, "operations", true)));
        var saved = saveInfrastructure(id, command);
        assertThat(saved)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.servers[0].aiConfirmed")
                .isEqualTo(true);
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/generated-documents", id)
                        .with(authentication(INFRA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiSupplement\":false}")
                        .exchange()
                        .getResponse()
                        .getContentAsString())
                .contains("4 vCPU", "16 GB", "합성 운영팀", "매일 백업", "AI 추론 · 담당자 확인");
        assertThat(complete(INFRA, review, INFRA_CHECKLIST)).hasStatusOk();
        command.put("expectedRevision", 2);
        command.put("draft", true);
        ((tools.jackson.databind.node.ObjectNode) command.path("connections").get(0)).put("sourceServerId", "missing");
        assertThat(saveInfrastructure(id, command)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        // 정보보안이 인프라 완료 뒤 운영 정보를 바꾸면 인프라 재협의 전 보안 완료를 막는다.
        ((tools.jackson.databind.node.ObjectNode) command.path("connections").get(0)).put("sourceServerId", "web");
        command.put("draft", false);
        ((tools.jackson.databind.node.ObjectNode) command.path("operations")).put("backup", "매일 백업 및 월간 복구 점검");
        testAnswers
                .allAnswers(testAccess.viewableRequest(reviewer, id))
                .forEach(a -> revisions.put(a.fieldKey(), a.revision()));
        command.set("answerRevisions", testJson.valueToTree(revisions));
        assertThat(saveInfrastructure(id, command)).hasStatusOk();
        assertThat(mvc.get().uri("/api/reviews/{id}", review).with(authentication(INFRA)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("RECHECK");
        Long securityReview = reviewId(reviews.getResponse().getContentAsString(), "SECURITY");
        assertThat(complete(SECURITY, securityReview, SECURITY_CHECKLIST)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(complete(INFRA, review, INFRA_CHECKLIST)).hasStatusOk();
        assertThat(complete(SECURITY, securityReview, SECURITY_CHECKLIST)).hasStatusOk();
        var comparison = mvc.get()
                .uri("/api/security-requests/{id}/consultation-comparison", id)
                .with(authentication(APPLICANT))
                .exchange();
        assertThat(comparison)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.baselineAvailable")
                .isEqualTo(true);
        String compared = comparison.getResponse().getContentAsString();
        assertThat(compared).contains("매일 백업 및 월간 복구 점검", "CONFIGURATION", "SECURITY", "합성 웹");
        String fingerprint = JsonPath.read(compared, "$.fingerprint");
        java.util.Set<String> summaryEvidenceKeys =
                new java.util.HashSet<>(JsonPath.<java.util.Map<String, Object>>read(compared, "$.current")
                        .keySet());
        summaryEvidenceKeys.addAll(JsonPath.<java.util.Map<String, Object>>read(compared, "$.baseline")
                .keySet());
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/consultation-summaries", id)
                        .with(authentication(SECURITY))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedFingerprint\":\"" + fingerprint + "\"}"))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/consultation-summaries", id)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedFingerprint\":\"old\"}"))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/consultation-summaries", id)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedFingerprint\":\"" + fingerprint + "\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.items[0].evidenceIds[0]")
                .isIn(summaryEvidenceKeys);
        if (docType.equals("FIREWALL")) {
            answerAsApplicant(id, "firewall.purpose", "사용자가 최종 검토에서 보완한 합성 목적");
            var updatedComparison = mvc.get()
                    .uri("/api/security-requests/{id}/consultation-comparison", id)
                    .with(authentication(APPLICANT))
                    .exchange();
            String updatedBody = updatedComparison.getResponse().getContentAsString();
            assertThat((String) JsonPath.read(updatedBody, "$.fingerprint")).isNotEqualTo(fingerprint);
            assertThat((String) JsonPath.read(updatedBody, "$.summary.fingerprint"))
                    .isEqualTo(fingerprint);
            assertThat(updatedBody).contains("APPLICANT", "사용자가 최종 검토에서 보완한 합성 목적");
        }
    }

    private MvcTestResult saveInfrastructure(Long id, Object command) {
        return mvc.put()
                .uri("/api/security-requests/{id}/infrastructure-plan", id)
                .with(authentication(SECURITY))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(testJson.writeValueAsString(command))
                .exchange();
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

    private void answerAsApplicant(Long requestId, String fieldKey, String value) throws Exception {
        long revision = answerRevision(requestId, fieldKey);
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/answers/{key}", requestId, fieldKey)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value + "\",\"expectedRevision\":" + revision + "}"))
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

    private long answerRevision(Long requestId, String fieldKey) throws Exception {
        List<Number> revisions =
                JsonPath.read(requestJson(requestId), "$.answers[?(@.fieldKey=='" + fieldKey + "')].revision");
        return revisions.get(0).longValue();
    }

    private boolean answerNeedsReview(Long requestId, String fieldKey) throws Exception {
        List<Boolean> flags =
                JsonPath.read(requestJson(requestId), "$.answers[?(@.fieldKey=='" + fieldKey + "')].needsReview");
        return flags.get(0);
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

    private Long createFirewallRequest() throws Exception {
        return createReviewRequest("FIREWALL");
    }

    private Long createReviewRequest(String docType) throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"검토 흐름 테스트\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\""
                        + docType + "\"]}")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        Long requestId = ((Number)
                        JsonPath.read(created.getResponse().getContentAsString(), "$.requests[0].securityRequestId"))
                .longValue();
        if (docType.equals("FIREWALL")) {
            answerAsApplicantNew(requestId, "firewall.port", "443");
            answerAsApplicantNew(requestId, "firewall.purpose", "초안 목적");
        }
        return requestId;
    }

    /** 아직 답변이 없는 항목(처음 답변 — expectedRevision 없음). */
    private void answerAsApplicantNew(Long requestId, String fieldKey, String value) {
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/answers/{key}", requestId, fieldKey)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value + "\"}"))
                .hasStatusOk();
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
