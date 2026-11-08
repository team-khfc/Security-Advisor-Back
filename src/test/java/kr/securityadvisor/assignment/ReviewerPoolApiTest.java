package kr.securityadvisor.assignment;

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
 * F-12: 관리자가 분야별 담당자 목록을 등록·기본 지정·비활성화한다. 분야당 활성 기본 담당자 1명이 유지되고,
 * 비활성화는 배정을 바꾸지 않고 "재배정 필요" 신청을 돌려준다. 다른 테스트와 DB 를 공유하므로 끝에 seed 상태(infra 기본)로 되돌린다.
 */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReviewerPoolApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication INFRA = auth("infra", "박인프라", MemberRole.INFRA);
    private static final Authentication ADMIN = auth("admin", "최관리", MemberRole.ADMIN);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("역할 불일치 422 → 역할 부여 후 등록·기본 지정 → 재배정 → 비활성화(재배정 필요) → seed 복원")
    void managePool() throws Exception {
        String username = "pool.infra2";
        signUp(username);
        assertThat(mvc.get().uri("/api/reviewer-pools").with(authentication(INFRA)))
                .hasStatus(HttpStatus.FORBIDDEN);
        // APPLICANT 역할인 계정은 INFRA 담당자로 등록할 수 없다
        assertThat(register(username, false)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(mvc.put()
                        .uri("/api/members/{u}/role", username)
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"INFRA\",\"reason\":\"담당자 지정\"}"))
                .hasStatusOk();
        MvcTestResult created = register(username, false);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).headers().containsHeader("Location");
        Long poolId =
                ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.reviewerPoolId")).longValue();
        assertThat(created).bodyJson().extractingPath("$.defaultReviewer").isEqualTo(false);
        // 중복 등록은 422
        assertThat(register(username, false)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);

        Long seedInfraPoolId = poolIdOf("infra");
        // 기본 담당자 지정 → 이전 기본(infra) 해제, 분야당 기본 1명
        assertThat(mvc.put()
                        .uri("/api/reviewer-pools/{id}/default", poolId)
                        .with(authentication(ADMIN))
                        .with(csrf()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.defaultReviewer")
                .isEqualTo(true);
        assertThat(mvc.get().uri("/api/reviewer-pools?field=INFRA").with(authentication(ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.defaultReviewer==true && @.active==true)].memberUsername")
                .isEqualTo(List.of(username));

        // 새 기본 담당자에게 자동 배정되는지: 신청 → 협의 요청
        Long requestId = createFirewallRequest();
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/reviews", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.get()
                        .uri("/api/security-requests/{id}/assignments", requestId)
                        .with(authentication(ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.field=='INFRA' && @.active==true)].assigneeUsername")
                .isEqualTo(List.of(username));

        // 비활성화 → 기본 해제, 그 사람의 활성 배정이 남은 신청이 재배정 필요 목록에 담긴다(배정 자체는 그대로)
        MvcTestResult deactivated = mvc.put()
                .uri("/api/reviewer-pools/{id}/activation", poolId)
                .with(authentication(ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false,\"reason\":\"인사 이동(합성)\"}")
                .exchange();
        assertThat(deactivated).hasStatusOk();
        assertThat(deactivated).bodyJson().extractingPath("$.pool.active").isEqualTo(false);
        assertThat(deactivated)
                .bodyJson()
                .extractingPath("$.pool.defaultReviewer")
                .isEqualTo(false);
        List<Integer> need =
                JsonPath.read(deactivated.getResponse().getContentAsString(), "$.reassignmentNeededRequestIds");
        assertThat(need).contains(requestId.intValue());
        // 비활성 담당자로는 재배정·기본 지정 불가
        assertThat(mvc.put()
                        .uri("/api/reviewer-pools/{id}/default", poolId)
                        .with(authentication(ADMIN))
                        .with(csrf()))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);

        // seed 복원: infra 를 다시 기본으로, 그 신청은 infra 에게 재배정
        assertThat(mvc.put()
                        .uri("/api/reviewer-pools/{id}/default", seedInfraPoolId)
                        .with(authentication(ADMIN))
                        .with(csrf()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.memberUsername")
                .isEqualTo("infra");
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/assignments", requestId)
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"INFRA\",\"assigneeUsername\":\"infra\",\"reason\":\"담당자 비활성화\"}"))
                .hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult register(String username, boolean defaultReviewer) {
        return mvc.post()
                .uri("/api/reviewer-pools")
                .with(authentication(ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"field\":\"INFRA\",\"memberUsername\":\"" + username + "\",\"defaultReviewer\":"
                        + defaultReviewer + "}")
                .exchange();
    }

    private Long poolIdOf(String username) throws Exception {
        MvcTestResult list = mvc.get()
                .uri("/api/reviewer-pools?field=INFRA")
                .with(authentication(ADMIN))
                .exchange();
        List<Integer> ids = JsonPath.read(
                list.getResponse().getContentAsString(), "$[?(@.memberUsername=='" + username + "')].reviewerPoolId");
        return ids.get(0).longValue();
    }

    private Long createFirewallRequest() throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"담당자 목록 테스트\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.requests[0].securityRequestId"))
                .longValue();
    }

    private void signUp(String username) {
        assertThat(mvc.post()
                        .uri("/api/members")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"Synthetic-pass1\",\"displayName\":\"합성 담당자\"}"))
                .hasStatus(HttpStatus.CREATED);
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
