package kr.securityadvisor.llm;

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
 * F-03 AI 제안(fake 모드): 신청자 항목 중 값 없는 것에 AI_INFERRED·미확정 제안이 들어가고 근거가 붙는다.
 * 담당자 항목·확인된 값은 건드리지 않고, 사용자 확인 뒤에야 확정 값이 된다.
 */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = {"securityadvisor.demo.account-password=Test-Demo-1234", "securityadvisor.llm.mode=fake"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AiProposalApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication APPLICANT2 = auth("applicant2", "이신청", MemberRole.APPLICANT);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("AI 제안 → AI_INFERRED·미확정 저장, 담당자 항목 제외, 사용자 확인 후 재제안 시 확인된 값은 건드리지 않음")
    void proposeThenConfirm() throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"AI 제안 테스트\",\"summary\":\"외부 API 연동 사업(합성)\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        Long requestId = ((Number)
                        JsonPath.read(created.getResponse().getContentAsString(), "$.requests[0].securityRequestId"))
                .longValue();

        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/ai-proposals", requestId)
                        .with(authentication(APPLICANT2))
                        .with(csrf()))
                .hasStatus(HttpStatus.NOT_FOUND);

        MvcTestResult proposed = mvc.post()
                .uri("/api/security-requests/{id}/ai-proposals", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .exchange();
        assertThat(proposed).hasStatusOk();
        assertThat(proposed).bodyJson().extractingPath("$.modelReference").isEqualTo("fake/offline");
        String body = proposed.getResponse().getContentAsString();
        List<String> proposedKeys = JsonPath.read(body, "$.proposed[*].fieldKey");
        assertThat(proposedKeys)
                .contains("firewall.purpose", "firewall.period")
                .doesNotContain("network.source", "system.target_server");
        assertThat(proposed)
                .bodyJson()
                .extractingPath("$.proposed[0].valueSource")
                .isEqualTo("AI_INFERRED");
        assertThat(proposed)
                .bodyJson()
                .extractingPath("$.proposed[0].confirmed")
                .isEqualTo(false);
        assertThat(proposed)
                .bodyJson()
                .extractingPath("$.proposed[0].evidence")
                .asString()
                .contains("(S");

        // 미확정 AI 값은 제출을 막는다(기존 규칙). 사용자가 확인하면 확정 값이 되고, 재제안 시 그 항목은 제외된다
        MvcTestResult detail = mvc.get()
                .uri("/api/security-requests/{id}", requestId)
                .with(authentication(APPLICANT))
                .exchange();
        List<Number> revisions = JsonPath.read(
                detail.getResponse().getContentAsString(), "$.answers[?(@.fieldKey=='firewall.purpose')].revision");
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/answers/firewall.purpose/confirmation", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revisions.get(0) + "}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.confirmed")
                .isEqualTo(true);
        MvcTestResult again = mvc.post()
                .uri("/api/security-requests/{id}/ai-proposals", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldKeys\":[\"firewall.purpose\",\"firewall.period\"]}")
                .exchange();
        assertThat(again).hasStatusOk();
        List<String> againKeys = JsonPath.read(again.getResponse().getContentAsString(), "$.proposed[*].fieldKey");
        assertThat(againKeys).containsExactly("firewall.period");

        MvcTestResult trail = mvc.get()
                .uri("/api/security-requests/{id}/audit-events", requestId)
                .with(authentication(APPLICANT))
                .exchange();
        List<String> actions = JsonPath.read(trail.getResponse().getContentAsString(), "$[*].action");
        assertThat(actions).contains("ANSWER_AI_PROPOSED");
        // F-14: AI 호출마다 구성·용도·소요 시간이 남고 프롬프트·응답 원문은 남지 않는다
        assertThat(actions).contains("LLM_CALLED");
        List<String> llmSummaries =
                JsonPath.read(trail.getResponse().getContentAsString(), "$[?(@.action=='LLM_CALLED')].summary");
        assertThat(llmSummaries.get(0))
                .contains("INTERVIEW")
                .contains("fake/offline")
                .contains("소요 ");
    }

    @Test
    @DisplayName("AI 서류 유형 제안: 활성 유형 중 근거·확신도와 함께 제안(제안만), 개요·사업 건 둘 다 없으면 400")
    void docTypeSuggestions() throws Exception {
        MvcTestResult suggested = mvc.post()
                .uri("/api/doc-type-suggestions")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\":\"외부 공개 API 와 연동하는 민원 포털을 새로 만든다(합성)\"}")
                .exchange();
        assertThat(suggested).hasStatusOk();
        List<String> codes = JsonPath.read(suggested.getResponse().getContentAsString(), "$.suggestions[*].docType");
        assertThat(codes).contains("FIREWALL", "SECURITY_REVIEW");
        assertThat(suggested)
                .bodyJson()
                .extractingPath("$.suggestions[?(@.docType=='FIREWALL')].confidence")
                .isEqualTo(List.of("HIGH"));
        assertThat(suggested).bodyJson().extractingPath("$.modelReference").isEqualTo("fake/offline");
        assertThat(suggested).bodyJson().extractingPath("$.note").asString().contains("제안");
        assertThat(mvc.post()
                        .uri("/api/doc-type-suggestions")
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
