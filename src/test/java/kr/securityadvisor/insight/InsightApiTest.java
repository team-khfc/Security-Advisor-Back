package kr.securityadvisor.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** F-16: 지표는 담당자·관리자만 읽고, 저장 상태 없이 계산되며 개인 순위 항목이 없다. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InsightApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication SECURITY = auth("security", "정보안", MemberRole.SECURITY);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("요약 지표 조회: 신청자 403, 담당자 200(집계 키 존재, 개인별 키 없음)")
    void summary() throws Exception {
        assertThat(mvc.get().uri("/api/insights/summary").with(authentication(APPLICANT)))
                .hasStatus(HttpStatus.FORBIDDEN);
        MvcTestResult result = mvc.get()
                .uri("/api/insights/summary")
                .with(authentication(SECURITY))
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.activeDeadlines.ON_TRACK")
                .isNotNull();
        assertThat(result).bodyJson().extractingPath("$.supplements.OPEN").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.completedReviewCount").isNotNull();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("infra\"", "security\"", "username");
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
