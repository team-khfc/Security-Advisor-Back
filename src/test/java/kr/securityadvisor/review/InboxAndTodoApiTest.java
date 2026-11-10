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

/** F-04 요청함 필터·형제 신청 표시와 F-17 "처리할 일" 집계가 업무 상태를 따라 움직인다. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InboxAndTodoApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication INFRA = auth("infra", "박인프라", MemberRole.INFRA);
    private static final Authentication SECURITY = auth("security", "정보안", MemberRole.SECURITY);
    private static final Authentication ADMIN = auth("admin", "최관리", MemberRole.ADMIN);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("요청함 필터(상태·분야·서류·키워드) + 형제 신청 + 보완 수, 처리할 일 집계(담당자·신청자·관리자)")
    void inboxFilterAndTodo() throws Exception {
        String title = "요청함필터 합성사업 " + System.nanoTime();
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"" + title
                                + "\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\",\"SECURITY_REVIEW\"]}")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String body = created.getResponse().getContentAsString();
        Long firewallId = requestIdOf(body, "FIREWALL");
        Long reviewRequestId = requestIdOf(body, "SECURITY_REVIEW");

        long infraBefore = todo(INFRA, "$.pendingReviews");
        long applicantBefore = todo(APPLICANT, "$.openSupplements");

        MvcTestResult requested = mvc.post()
                .uri("/api/security-requests/{id}/reviews", firewallId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0}")
                .exchange();
        assertThat(requested).hasStatus(HttpStatus.CREATED);
        Long securityReviewId = reviewId(requested.getResponse().getContentAsString(), "SECURITY");
        assertThat(mvc.get().uri("/api/todos?page=0&size=100").with(authentication(INFRA)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.content[*].context.caseTitle")
                .asArray()
                .contains(title);

        // 요청함: 키워드(제목)로 걸러 한 건, 미배정 형제 신청(보안성검토)은 숨기고 미해결 보완 0
        MvcTestResult inbox = mvc.get()
                .uri("/api/reviews?keyword={k}", title)
                .with(authentication(INFRA))
                .exchange();
        assertThat(inbox).hasStatusOk();
        assertThat(inbox).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(inbox).bodyJson().extractingPath("$[0].review.field").isEqualTo("INFRA");
        assertThat(inbox).bodyJson().extractingPath("$[0].openSupplementCount").isEqualTo(0);
        assertThat(inbox)
                .bodyJson()
                .extractingPath("$[0].siblingRequests[*].securityRequestId")
                .isEqualTo(List.of());
        // 분야·서류·상태 필터 — 안 맞으면 비어 있고, 잘못된 상태 이름은 400
        assertThat(mvc.get()
                        .uri("/api/reviews?keyword={k}&field=SECURITY", title)
                        .with(authentication(INFRA)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .isEmpty();
        assertThat(mvc.get()
                        .uri("/api/reviews?keyword={k}&docType=FIREWALL&status=waiting&requestStatus=IN_REVIEW", title)
                        .with(authentication(INFRA)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .hasSize(1);
        assertThat(mvc.get().uri("/api/reviews?status=NOPE").with(authentication(INFRA)))
                .hasStatus(HttpStatus.BAD_REQUEST);
        // 신청 번호로도 찾는다
        assertThat(mvc.get()
                        .uri("/api/reviews?keyword={k}", String.valueOf(firewallId))
                        .with(authentication(INFRA)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .hasSize(1);

        // 처리할 일: INFRA 는 협의 대기 +1, ADMIN 은 미배정 0 건 증가(둘 다 배정됨)
        assertThat(todo(INFRA, "$.pendingReviews")).isEqualTo(infraBefore + 1);
        assertThat(todo(INFRA, "$.total")).isGreaterThanOrEqualTo(infraBefore + 1);
        assertThat(mvc.get().uri("/api/todo-summary").with(authentication(ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.role")
                .isEqualTo("ADMIN");

        // SECURITY 보완 요청 → 신청자 openSupplements +1, 요청함 보완 수 1
        long securityRevision = ((Number) JsonPath.read(
                        mvc.get()
                                .uri("/api/reviews/{id}", securityReviewId)
                                .with(authentication(SECURITY))
                                .exchange()
                                .getResponse()
                                .getContentAsString(),
                        "$.revision"))
                .longValue();
        MvcTestResult supplement = mvc.post()
                .uri("/api/reviews/{id}/supplements", securityReviewId)
                .with(authentication(SECURITY))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldKeys\":[\"firewall.purpose\"],\"reason\":\"목적 보완\",\"expectedRevision\":"
                        + securityRevision + "}")
                .exchange();
        assertThat(supplement).hasStatus(HttpStatus.CREATED);
        Long supplementId =
                ((Number) JsonPath.read(supplement.getResponse().getContentAsString(), "$.supplementId")).longValue();
        assertThat(todo(APPLICANT, "$.openSupplements")).isEqualTo(applicantBefore + 1);
        assertThat(mvc.get().uri("/api/reviews?keyword={k}", title).with(authentication(SECURITY)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].openSupplementCount")
                .isEqualTo(1);

        // 신청자 답변 → SECURITY 의 answeredSupplements +1, 신청자 openSupplements 원복
        long answeredBefore = todo(SECURITY, "$.answeredSupplements");
        assertThat(mvc.post()
                        .uri("/api/supplements/{id}/answers", supplementId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"보완했습니다\",\"expectedRevision\":0}"))
                .hasStatus(HttpStatus.CREATED);
        assertThat(todo(SECURITY, "$.answeredSupplements")).isEqualTo(answeredBefore + 1);
        assertThat(todo(APPLICANT, "$.openSupplements")).isEqualTo(applicantBefore);
    }

    private long todo(Authentication who, String path) throws Exception {
        MvcTestResult result =
                mvc.get().uri("/api/todo-summary").with(authentication(who)).exchange();
        assertThat(result).hasStatusOk();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
    }

    private static Long requestIdOf(String body, String docType) {
        List<Integer> ids = JsonPath.read(body, "$.requests[?(@.docType=='" + docType + "')].securityRequestId");
        return ids.get(0).longValue();
    }

    private static Long reviewId(String body, String field) {
        List<Integer> ids = JsonPath.read(body, "$.reviews[?(@.field=='" + field + "')].reviewId");
        return ids.get(0).longValue();
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
