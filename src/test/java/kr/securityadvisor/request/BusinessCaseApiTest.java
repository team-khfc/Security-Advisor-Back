package kr.securityadvisor.request;

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
 * 시연 필수 경로의 앞부분(F-01·F-03·F-05 철회)을 실제 DB·보안 설정과 함께 HTTP 로 확인한다.
 * 시연 계정은 DemoAccountSeeder 가 만든다(applicant·applicant2).
 */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BusinessCaseApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청");
    private static final Authentication OTHER_APPLICANT = auth("applicant2", "박신청");

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("사업 건 생성 → 상세 조회 → 공통·서류별 답변 → 담당자 확인 요청 → 신청 1건 철회(다른 신청 불변) → 남의 사업 건 404")
    void applicantFlow() throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"민원 포털 고도화\",\"summary\":\"민원 접수 자동화\",\"department\":\"정보화부\","
                        + "\"docTypes\":[\"FIREWALL\",\"SECURITY_REVIEW\"]}")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED).headers().containsHeader("Location");
        assertThat(created).bodyJson().extractingPath("$.progressSummary").isEqualTo("2건 중 2건 작성 중");
        assertThat(created).bodyJson().extractingPath("$.requests.length()").isEqualTo(2);
        // 생성 요청 값은 공통 답변(USER_ANSWER)으로, 담당자 항목은 담당자 확인 필요(출처 없음)로 미리 만들어진다.
        assertThat(created)
                .bodyJson()
                .extractingPath("$.caseAnswers[?(@.fieldKey=='project.name')].valueSource")
                .isEqualTo(List.of("USER_ANSWER"));
        assertThat(created)
                .bodyJson()
                .extractingPath("$.caseAnswers[?(@.fieldKey=='network.source')].confirmed")
                .isEqualTo(List.of(false));

        String body = created.getResponse().getContentAsString();
        Long caseId = ((Number) JsonPath.read(body, "$.businessCaseId")).longValue();
        Long firewallId = ((Number) JsonPath.read(body, "$.requests[0].securityRequestId")).longValue();
        Long reviewId = ((Number) JsonPath.read(body, "$.requests[1].securityRequestId")).longValue();

        // 공통 항목 답변(처음이라 expectedRevision 없음)
        assertThat(mvc.put()
                        .uri("/api/business-cases/{id}/answers/{key}", caseId, "data.personal_info")
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"true\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.valueSource")
                .isEqualTo("USER_ANSWER");

        // 서류별 항목 — 보안성검토 항목을 방화벽 신청에 넣으면 422
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/answers/{key}", firewallId, "review.logging")
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"로그 1년 보관\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/answers/{key}", firewallId, "firewall.port")
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"443\"}"))
                .hasStatusOk();

        // 담당자 확인 요청(기존 답변 revision 0)
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/answers/{key}/staff-check", firewallId, "firewall.port")
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.valueSource")
                .isNull();

        // 보안성검토 신청만 철회 → 방화벽 신청은 그대로(불변식 16)
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/withdrawal", reviewId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"사업 범위 축소\",\"expectedRevision\":0}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("WITHDRAWN");
        assertThat(mvc.get().uri("/api/business-cases/{id}", caseId).with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.progressSummary")
                .isEqualTo("2건 중 1건 작성 중, 1건 철회");

        // 철회된 신청의 답변은 422, 사유 없는 철회는 400
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/answers/{key}", reviewId, "review.logging")
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"값\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mvc.put()
                        .uri("/api/security-requests/{id}/withdrawal", firewallId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\",\"expectedRevision\":0}"))
                .hasStatus(HttpStatus.BAD_REQUEST);

        // 다른 신청자는 존재 여부도 알 수 없다(404)
        assertThat(mvc.get().uri("/api/business-cases/{id}", caseId).with(authentication(OTHER_APPLICANT)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/security-requests/{id}", firewallId).with(authentication(OTHER_APPLICANT)))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("서류 유형 없이 만들면 400, 로그인 없이 만들면 401")
    void validation() {
        assertThat(mvc.post()
                        .uri("/api/business-cases")
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"민원 포털\",\"docTypes\":[]}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.post()
                        .uri("/api/business-cases")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"민원 포털\",\"docTypes\":[\"FIREWALL\"]}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("기존 사업 건에 서류 추가: 1단계 신청이 생기고 공통 답변을 공유, 같은 유형 중복·게시본에 없는 유형은 422, 남의 사업 건은 404")
    void addRequestToExistingCase() throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"서류 추가 테스트\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        Long caseId =
                ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.businessCaseId")).longValue();

        MvcTestResult added = mvc.post()
                .uri("/api/business-cases/{id}/security-requests", caseId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"docType\":\"SECURITY_REVIEW\"}")
                .exchange();
        assertThat(added).hasStatus(HttpStatus.CREATED);
        assertThat(added).bodyJson().extractingPath("$.docType").isEqualTo("SECURITY_REVIEW");
        assertThat(added).bodyJson().extractingPath("$.stage").isEqualTo(1);
        assertThat(mvc.get().uri("/api/business-cases/{id}", caseId).with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.requests.length()")
                .isEqualTo(2);
        assertThat(mvc.post()
                        .uri("/api/business-cases/{id}/security-requests", caseId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docType\":\"SECURITY_REVIEW\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mvc.post()
                        .uri("/api/business-cases/{id}/security-requests", caseId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docType\":\"NO_SUCH_TYPE\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mvc.post()
                        .uri("/api/business-cases/{id}/security-requests", caseId)
                        .with(authentication(OTHER_APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docType\":\"FIREWALL\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("게시본에 없는 서류 유형 코드는 422, 형식이 틀린 코드는 400")
    void unknownDocType() {
        assertThat(mvc.post()
                        .uri("/api/business-cases")
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"민원 포털\",\"docTypes\":[\"USB_REQUEST\"]}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mvc.post()
                        .uri("/api/business-cases")
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"민원 포털\",\"docTypes\":[\"usb request!\"]}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("현재 게시본과 seed 게시본(v1)을 조회할 수 있다")
    void currentPublication() {
        // 다른 테스트(F-08)가 새 버전을 게시할 수 있으므로 seed 항목 수는 v1(게시본 1)로 확인한다
        assertThat(mvc.get().uri("/api/publications/{id}", 1).with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.fieldSchema.fields.length()")
                .isEqualTo(16);
        assertThat(mvc.get().uri("/api/publications/current").with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.fieldSchema.fields.length()")
                .asNumber()
                .satisfies(n -> assertThat(n.intValue()).isGreaterThanOrEqualTo(16));
        assertThat(mvc.get().uri("/api/publications/current").with(authentication(APPLICANT)))
                .bodyJson()
                .extractingPath("$.fieldSchema.docTypes[0].code")
                .isEqualTo("FIREWALL");
    }

    private static Authentication auth(String username, String displayName) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, MemberRole.APPLICANT);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(MemberRole.APPLICANT.authority())));
    }
}
