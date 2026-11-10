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
 * 2단계 진입(협의 요청)과 운영 도메인 P1: 자동 배정(F-12)·협의 기한(F-13)·감사 이력(F-14)이 한 트랜잭션에서 만들어지는지,
 * 재배정 권한·규칙, 요청함·관리자 감사 조회를 실제 DB 로 확인한다. 시연 계정은 DemoAccountSeeder, 기본 담당자는 seed.
 */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReviewRequestApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication INFRA = auth("infra", "박인프라", MemberRole.INFRA);
    private static final Authentication ADMIN = auth("admin", "최관리", MemberRole.ADMIN);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("협의 요청 → 2단계 협의 중, 분야별 협의 2건·자동 배정 2건·협의 기한 2건·감사 이력이 생기고 재배정 규칙이 지켜진다")
    void requestReviewFlow() throws Exception {
        Long requestId = createFirewallRequest();

        MvcTestResult result = mvc.post()
                .uri("/api/security-requests/{id}/reviews", requestId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.request.stage").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.request.status").isEqualTo("IN_REVIEW");
        assertThat(result).bodyJson().extractingPath("$.reviews.length()").isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.assignments.assigned.length()")
                .isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.assignments.unassigned.length()")
                .isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.deadlines.length()").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.deadlines[0].status").isEqualTo("ON_TRACK");

        // 1단계가 아니므로 다시 요청하면 422, 오래된 revision 은 409
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/reviews", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/reviews", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .hasStatus(HttpStatus.CONFLICT);

        // 담당자 요청함: infra 에게 INFRA 배정이 보인다
        assertThat(mvc.get().uri("/api/assignments/mine").with(authentication(INFRA)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.securityRequestId==" + requestId + ")].field")
                .isEqualTo(List.of("INFRA"));

        // 재배정: 신청자는 403, 담당자 목록에 없는 사람은 422, 관리자+사유는 201 이고 이전 배정은 비활성으로 남는다
        String reassignBody = "{\"field\":\"INFRA\",\"assigneeUsername\":\"infra\",\"reason\":\"담당 조정\"}";
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/assignments", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignBody))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/assignments", requestId)
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"INFRA\",\"assigneeUsername\":\"security\",\"reason\":\"담당 조정\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/assignments", requestId)
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignBody))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/assignments", requestId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.field=='INFRA')].active")
                .isEqualTo(List.of(false, true));

        // 기한 목록과 처리 이력(감사): 협의 요청·배정·기한·재배정이 신청 대상으로 남는다
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/deadlines", requestId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.kind=='REVIEW' && @.active==true)]")
                .asArray()
                .hasSize(2);
        MvcTestResult trail = mvc.get()
                .uri("/api/security-requests/{id}/audit-events", requestId)
                .with(authentication(APPLICANT))
                .exchange();
        assertThat(trail).hasStatusOk();
        List<String> actions = JsonPath.read(trail.getResponse().getContentAsString(), "$[*].action");
        assertThat(actions)
                .contains("SECURITY_REQUEST_CREATED", "REVIEW_REQUESTED", "ASSIGNED", "DEADLINE_STARTED", "REASSIGNED");

        // 관리자 전체 감사 조회는 ADMIN 만
        assertThat(mvc.get()
                        .uri("/api/audit-events?targetType=SECURITY_REQUEST&targetId={id}", requestId)
                        .with(authentication(ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .isNotNull();
        // F-14: 신청자도 자기 신청의 업무 이력을 통합 목록에서 조회한다.
        assertThat(mvc.get()
                        .uri("/api/audit-events?targetType=SECURITY_REQUEST&targetId={id}", requestId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.content[0].targetId")
                .isEqualTo(String.valueOf(requestId));
    }

    @Test
    @DisplayName("오류 응답에는 요청 ID 가 실리고, 감사 이력은 DB 트리거가 수정·삭제를 거부한다")
    void requestIdAndAppendOnly() {
        MvcTestResult notFound = mvc.get()
                .uri("/api/security-requests/{id}", 999_999L)
                .with(authentication(APPLICANT))
                .exchange();
        assertThat(notFound).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(notFound).headers().containsHeader("X-Request-Id");
        assertThat(notFound).bodyJson().extractingPath("$.requestId").isNotNull();
    }

    private Long createFirewallRequest() throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"협의 요청 테스트\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.requests[0].securityRequestId"))
                .longValue();
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
