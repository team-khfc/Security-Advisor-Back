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

/** 담당자 AI 질의(fake 모드): 배정된 담당자만 대화를 열고, 기관 사실은 역할로 걸러 주입되며, 대화는 본인만 본다. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = {"securityadvisor.demo.account-password=Test-Demo-1234", "securityadvisor.llm.mode=fake"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InquiryApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication INFRA = auth("infra", "박인프라", MemberRole.INFRA);
    private static final Authentication SECURITY = auth("security", "정보안", MemberRole.SECURITY);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("협의 요청 후 배정된 INFRA 가 대화를 열고 질문하면 기관 사실이 주입된 답변이 저장된다. 신청자는 403, 다른 담당자는 404")
    void inquiryFlow() throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"질의 테스트\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        Long requestId = ((Number)
                        JsonPath.read(created.getResponse().getContentAsString(), "$.requests[0].securityRequestId"))
                .longValue();

        // 1단계(미배정)에서는 INFRA 도 403, 신청자는 항상 403
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/inquiries", requestId)
                        .with(authentication(INFRA))
                        .with(csrf()))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/reviews", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/inquiries", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf()))
                .hasStatus(HttpStatus.FORBIDDEN);

        MvcTestResult opened = mvc.post()
                .uri("/api/security-requests/{id}/inquiries", requestId)
                .with(authentication(INFRA))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"서버 확인\"}")
                .exchange();
        assertThat(opened).hasStatus(HttpStatus.CREATED);
        assertThat(opened).bodyJson().extractingPath("$.field").isEqualTo("INFRA");
        Long inquiryId = ((Number) JsonPath.read(opened.getResponse().getContentAsString(), "$.inquiryId")).longValue();

        MvcTestResult turn = mvc.post()
                .uri("/api/inquiries/{id}/messages", inquiryId)
                .with(authentication(INFRA))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"WAS-01 서버의 망 구역과 IP 를 확인해 줘\"}")
                .exchange();
        assertThat(turn).hasStatus(HttpStatus.CREATED);
        assertThat(turn).bodyJson().extractingPath("$.question.role").isEqualTo("USER");
        assertThat(turn).bodyJson().extractingPath("$.answer.role").isEqualTo("ASSISTANT");
        assertThat(turn).bodyJson().extractingPath("$.answer.modelReference").isEqualTo("fake/offline");
        // fake 응답은 주입된 사실 블록을 되돌려 준다 — 별칭 WAS-01 로 매칭된 합성 서버 행(10.0.1.10)은 사실 값이라 가려지지 않는다
        assertThat(turn)
                .bodyJson()
                .extractingPath("$.answer.text")
                .asString()
                .contains("합성 업무서버 01")
                .contains("10.0.1.10");
        assertThat(turn).bodyJson().extractingPath("$.answer.flagged").isEqualTo(false);

        assertThat(mvc.get().uri("/api/inquiries/{id}/messages", inquiryId).with(authentication(INFRA)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].role")
                .isEqualTo(List.of("USER", "ASSISTANT"));
        assertThat(mvc.get().uri("/api/inquiries/{id}/messages", inquiryId).with(authentication(SECURITY)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/inquiries", requestId)
                        .with(authentication(INFRA)))
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
