package kr.securityadvisor.insight;

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

/** F-16: 지표는 관리자만 읽고, 저장 상태 없이 계산되며 개인 순위 항목이 없다. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InsightApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication SECURITY = auth("security", "정보안", MemberRole.SECURITY);

    private static final Authentication ADMIN = auth("admin", "최관리", MemberRole.ADMIN);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("요약 지표 조회: 신청자·담당자 403, 관리자 200(개인별 키 없음)")
    void summary() throws Exception {
        assertThat(mvc.get().uri("/api/insights/summary").with(authentication(APPLICANT)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/insights/summary").with(authentication(SECURITY)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get()
                        .uri("/api/insights/summary")
                        .with(authentication(auth("infra", "합성 인프라", MemberRole.INFRA))))
                .hasStatus(HttpStatus.FORBIDDEN);
        MvcTestResult result = mvc.get()
                .uri("/api/insights/summary")
                .with(authentication(ADMIN))
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.activeDeadlines.ON_TRACK")
                .isNotNull();
        assertThat(result).bodyJson().extractingPath("$.supplements.OPEN").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.completedReviewCount").isNotNull();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("infra\"", "security\"", "username");

        // 서류 유형별 수·단계별 소요 영업일·비율 지표는 업무 상태를 따라 움직인다(신청 생성 → 협의 요청 = 1단계 0영업일)
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"지표 테스트\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        Long requestId = ((Number)
                        JsonPath.read(created.getResponse().getContentAsString(), "$.requests[0].securityRequestId"))
                .longValue();
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/reviews", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .hasStatus(HttpStatus.CREATED);
        MvcTestResult after = mvc.get()
                .uri("/api/insights/summary")
                .with(authentication(ADMIN))
                .exchange();
        assertThat(after).hasStatusOk();
        String body = after.getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(body, "$.requestsByDocType.FIREWALL")).longValue())
                .isGreaterThanOrEqualTo(1);
        assertThat(((Number) JsonPath.read(body, "$.completedByStage['1']")).longValue())
                .isGreaterThanOrEqualTo(1);
        assertThat(((Number) JsonPath.read(body, "$.averageBusinessDaysByStage['1']")).doubleValue())
                .isGreaterThanOrEqualTo(0.0);
        assertThat(after).bodyJson().extractingPath("$.overdueDeadlines").isNotNull();
        assertThat(after).bodyJson().extractingPath("$.aiProposals").isNotNull();
        // 협의를 요청한 신청이 있으므로 신청당 보완 횟수·차수 반환률은 계산된다(0 이상)
        assertThat(((Number) JsonPath.read(body, "$.supplementsPerRequest")).doubleValue())
                .isGreaterThanOrEqualTo(0);
        assertThat(((Number) JsonPath.read(body, "$.stageReturnRate")).doubleValue())
                .isBetween(0.0, 1.0);
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
