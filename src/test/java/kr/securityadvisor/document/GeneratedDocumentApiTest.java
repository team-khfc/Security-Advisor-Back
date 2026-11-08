package kr.securityadvisor.document;

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

/** F-06: 초안은 현재 답변으로 렌더링되고 미확정·담당자 항목이 "담당자 확인 필요"로 드러나며, AI 보완은 미확정 서술로 붙는다. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = {"securityadvisor.demo.account-password=Test-Demo-1234", "securityadvisor.llm.mode=fake"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GeneratedDocumentApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication APPLICANT2 = auth("applicant2", "이신청", MemberRole.APPLICANT);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("초안 생성 → 섹션·출처·상태 표기, 미확정 수, Markdown 다운로드. 다른 신청자는 404. 기관 정보 API 는 인증 없이 조회")
    void draftAndDownload() throws Exception {
        assertThat(mvc.get().uri("/api/installation"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("DEMO");

        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"서류 테스트\",\"summary\":\"개요(합성)\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        Long requestId = ((Number)
                        JsonPath.read(created.getResponse().getContentAsString(), "$.requests[0].securityRequestId"))
                .longValue();
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/answers/firewall.port", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"443\"}"))
                .hasStatusOk();

        MvcTestResult generated = mvc.post()
                .uri("/api/security-requests/{id}/generated-documents", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aiSupplement\":true}")
                .exchange();
        assertThat(generated).hasStatus(HttpStatus.CREATED);
        assertThat(generated).bodyJson().extractingPath("$.kind").isEqualTo("DRAFT");
        assertThat(generated).bodyJson().extractingPath("$.version").isEqualTo(1);
        assertThat(generated).bodyJson().extractingPath("$.sections[0].title").isEqualTo("공통 정보(사업 건)");
        assertThat(generated)
                .bodyJson()
                .extractingPath("$.sections[1].values[?(@.fieldKey=='firewall.port')].display")
                .isEqualTo(List.of("443"));
        assertThat(generated)
                .bodyJson()
                .extractingPath("$.sections[0].values[?(@.fieldKey=='network.source')].display")
                .isEqualTo(List.of("담당자 확인 필요"));
        assertThat(generated)
                .bodyJson()
                .extractingPath("$.unconfirmedCount")
                .asNumber()
                .satisfies(n -> assertThat(n.intValue()).isGreaterThan(0));
        assertThat(generated)
                .bodyJson()
                .extractingPath("$.aiSupplement")
                .asString()
                .contains("합성 초안");
        assertThat(generated).bodyJson().extractingPath("$.modelReference").isEqualTo("fake/offline");
        assertThat(generated)
                .bodyJson()
                .extractingPath("$.markdown")
                .asString()
                .contains("합성 시연 기관")
                .contains("| 포트 | 443 | 사용자 답변 | 확인 완료 |")
                .contains("AI 보완 서술 (미확정");
        Long documentId = ((Number)
                        JsonPath.read(generated.getResponse().getContentAsString(), "$.generatedDocumentId"))
                .longValue();

        MvcTestResult download = mvc.get()
                .uri("/api/generated-documents/{id}/download", documentId)
                .with(authentication(APPLICANT))
                .exchange();
        assertThat(download).hasStatusOk();
        assertThat(download)
                .headers()
                .hasValue(
                        "Content-Disposition",
                        "attachment; filename*=UTF-8''"
                                + java.net.URLEncoder.encode(
                                                "초안_" + requestId + "_v1.md", java.nio.charset.StandardCharsets.UTF_8)
                                        .replace("+", "%20"));
        assertThat(download.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .startsWith("# 방화벽 정책 허용 신청서");
        assertThat(mvc.get().uri("/api/generated-documents/{id}", documentId).with(authentication(APPLICANT2)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/generated-documents", requestId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.length()")
                .isEqualTo(1);
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
