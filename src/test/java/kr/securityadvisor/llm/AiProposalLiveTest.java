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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
 * 실제 모델 호출 검증(live). 환경변수 OPENROUTER_API_KEY 가 있을 때만 실행된다 — 키는 코드·설정 파일에 넣지 않는다.
 * OpenRouter 는 OpenAI 호환 API 라 SpringAiLlmGateway 가 그대로 쓴다. 모델은 OPENROUTER_MODEL(기본 deepseek/deepseek-v4.1-flash-1017 — seed 구성과 같다).
 * 실행: $env:OPENROUTER_API_KEY="..."; .\gradlew.bat test --tests "*AiProposalLiveTest*"
 */
@Tag("docker")
@Tag("live")
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
@SpringBootTest(
        properties = {
            "securityadvisor.demo.account-password=Test-Demo-1234",
            "securityadvisor.llm.mode=live",
            "securityadvisor.llm.allowed-hosts=openrouter.ai"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AiProposalLiveTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication ADMIN = auth("admin", "최관리", MemberRole.ADMIN);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("[live] OpenRouter 를 통한 실제 모델 호출로 근거 있는 AI 제안이 만들어진다")
    void liveProposal() throws Exception {
        String model = System.getenv().getOrDefault("OPENROUTER_MODEL", "deepseek/deepseek-v4.1-flash-1017");
        // 구성 등록(관리자 API, 키는 환경변수 이름만) — seed 구성은 키가 없어 선택되지 않는다
        assertThat(mvc.post()
                        .uri("/api/llm-configurations")
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"openrouter-live\",\"provider\":\"OPENAI_COMPATIBLE\","
                                + "\"baseUrl\":\"https://openrouter.ai/api/v1\",\"model\":\"" + model + "\","
                                + "\"apiKeyEnv\":\"OPENROUTER_API_KEY\",\"purposes\":[\"INTERVIEW\"],\"priority\":1,"
                                + "\"maxTokens\":4096,\"timeoutSeconds\":120}")) // 추론형 모델은 사고 토큰이 응답 상한을 먹으므로 넉넉히
                .hasStatus(HttpStatus.CREATED);

        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"민원 포털 외부 API 연동(합성)\","
                        + "\"summary\":\"민원 포털이 외부 기관의 공개 API(HTTPS)를 호출해 조회 결과를 보여주는 사업. 개인정보는 다루지 않는다(합성 시나리오)\","
                        + "\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        Long requestId = ((Number)
                        JsonPath.read(created.getResponse().getContentAsString(), "$.requests[0].securityRequestId"))
                .longValue();
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/answers/firewall.protocol", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"TCP\"}"))
                .hasStatusOk();

        MvcTestResult proposed = mvc.post()
                .uri("/api/security-requests/{id}/ai-proposals", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldKeys\":[\"firewall.purpose\",\"firewall.port\",\"firewall.period\"]}")
                .exchange();
        assertThat(proposed).hasStatusOk();
        String body = proposed.getResponse().getContentAsString();
        System.out.println("[live] modelReference=" + JsonPath.read(body, "$.modelReference"));
        System.out.println("[live] proposed=" + JsonPath.read(body, "$.proposed[*]['fieldKey','value','evidence']"));
        System.out.println("[live] skipped=" + JsonPath.read(body, "$.skipped"));
        assertThat(proposed)
                .bodyJson()
                .extractingPath("$.modelReference")
                .asString()
                .startsWith("openrouter-live/");
        List<String> keys = JsonPath.read(body, "$.proposed[*].fieldKey");
        assertThat(keys).isNotEmpty();
        List<String> evidence = JsonPath.read(body, "$.proposed[*].evidence");
        assertThat(evidence).allSatisfy(e -> assertThat(e).contains("(S"));
        assertThat(proposed)
                .bodyJson()
                .extractingPath("$.proposed[0].valueSource")
                .isEqualTo("AI_INFERRED");
        assertThat(proposed)
                .bodyJson()
                .extractingPath("$.proposed[0].confirmed")
                .isEqualTo(false);
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
