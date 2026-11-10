package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 실제 DB에서 항목별 권한과 형식 검증의 우회·부분 저장을 차단한다. */
@org.junit.jupiter.api.Tag("docker")
@SpringBootTest(properties = {"securityadvisor.demo.account-password=Test-Demo-1234", "securityadvisor.llm.mode=fake"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnswerFieldPolicyApiTest {
    @Autowired
    MockMvcTester mvc;

    @Test
    @org.junit.jupiter.api.DisplayName("미해당은 별도 상태로 조회되며 재입력·충돌·필수 거부·일괄 롤백을 지킨다")
    void notApplicableRoundTrip() throws Exception {
        var created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(auth(MemberRole.APPLICANT)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"합성 미해당 검증\",\"summary\":\"합성\",\"department\":\"합성부서\",\"docTypes\":[\"SECURITY_REVIEW\"]}")
                .exchange();
        assertThat(created).hasStatus(201);
        long id = number(created.getResponse().getContentAsString(), "$.requests[0].securityRequestId");
        String base = "/api/security-requests/" + id;
        var saved = put(base + "/answers/review.encryption", MemberRole.APPLICANT, "{\"notApplicable\":true}");
        assertThat(saved)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.notApplicable")
                .isEqualTo(true);
        long revision = number(saved.getResponse().getContentAsString(), "$.revision");
        assertThat(mvc.get().uri(base).with(authentication(auth(MemberRole.APPLICANT))))
                .bodyJson()
                .extractingPath("$.answers[?(@.fieldKey=='review.encryption')].notApplicable")
                .isEqualTo(List.of(true));
        assertThat(put(
                        base + "/answers/review.encryption",
                        MemberRole.APPLICANT,
                        "{\"value\":\"합성 암호화\",\"expectedRevision\":" + revision + "}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.notApplicable")
                .isEqualTo(false);
        assertThat(put(
                        base + "/answers/review.encryption",
                        MemberRole.APPLICANT,
                        "{\"notApplicable\":true,\"expectedRevision\":" + revision + "}"))
                .hasStatus(409);
        assertThat(put(
                        base + "/answers/review.encryption",
                        MemberRole.APPLICANT,
                        "{\"notApplicable\":true,\"value\":\"동시 입력\"}"))
                .hasStatus(422);
        long caseId = number(created.getResponse().getContentAsString(), "$.businessCaseId");
        assertThat(put(
                        "/api/business-cases/" + caseId + "/answers/project.period",
                        MemberRole.APPLICANT,
                        "{\"notApplicable\":true}"))
                .hasStatus(422);
    }

    @Test
    void blocksApplicantEditingInfraOwnedField() throws Exception {
        String created = create();
        long id = number(created, "$.businessCaseId");
        List<Number> revisions = JsonPath.read(created, "$.caseAnswers[?(@.fieldKey=='network.source')].revision");
        MvcTestResult result = put(
                "/api/business-cases/" + id + "/answers/network.source",
                MemberRole.APPLICANT,
                "{\"value\":\"192.0.2.123\",\"expectedRevision\":" + revisions.getFirst() + "}");
        assertThat(result).hasStatus(403);
        assertThat(put(
                        "/api/business-cases/" + id + "/answers/network.source/confirmation",
                        MemberRole.APPLICANT,
                        "{\"expectedRevision\":0}"))
                .hasStatus(403);
    }

    @Test
    void rejectsInvalidBooleanAndDate() throws Exception {
        long id = number(create(), "$.businessCaseId");
        assertThat(put(
                        "/api/business-cases/" + id + "/answers/data.personal_info",
                        MemberRole.APPLICANT,
                        "{\"value\":\"not-a-boolean\"}"))
                .hasStatus(422);
        assertThat(put(
                        "/api/business-cases/" + id + "/answers/project.period",
                        MemberRole.APPLICANT,
                        "{\"value\":\"not-a-date-range\"}"))
                .hasStatus(422);
    }

    @Test
    void blocksSecurityEditingInfraOwnedField() throws Exception {
        String created = create();
        long requestId = number(created, "$.requests[0].securityRequestId");
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/reviews", requestId)
                        .with(authentication(auth(MemberRole.APPLICANT)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .hasStatus(201);
        String reviews = mvc.get()
                .uri("/api/security-requests/{id}/reviews", requestId)
                .with(authentication(auth(MemberRole.SECURITY)))
                .exchange()
                .getResponse()
                .getContentAsString();
        List<Number> securityIds = JsonPath.read(reviews, "$[?(@.field=='SECURITY')].reviewId");
        assertThat(put(
                        "/api/reviews/" + securityIds.getFirst() + "/answers/network.source",
                        MemberRole.SECURITY,
                        "{\"value\":\"192.0.2.124\"}"))
                .hasStatus(403);
        assertThat(put(
                        "/api/reviews/" + securityIds.getFirst() + "/answers/network.source/confirmation",
                        MemberRole.SECURITY,
                        "{\"expectedRevision\":0}"))
                .hasStatus(403);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("일괄 저장의 뒤 항목이 잘못되면 앞 정상 항목도 롤백한다")
    void batchRollback() throws Exception {
        long id = number(create(), "$.businessCaseId");
        for (String bad : new String[] {"data.personal_info", "network.source"}) {
            var result = mvc.patch()
                    .uri("/api/business-cases/{id}/answers", id)
                    .with(authentication(auth(MemberRole.APPLICANT)))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            "{\"answers\":[{\"fieldKey\":\"project.period\",\"value\":\"2028-01-01~2028-12-31\"},{\"fieldKey\":\""
                                    + bad + "\",\"value\":\"invalid\"}]}");
            assertThat(result).hasStatus(bad.equals("network.source") ? 403 : 422);
            assertThat(mvc.get().uri("/api/business-cases/{id}", id).with(authentication(auth(MemberRole.APPLICANT))))
                    .bodyJson()
                    .extractingPath("$.caseAnswers[?(@.fieldKey=='project.period')]")
                    .asArray()
                    .isEmpty();
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("담당자 일괄 저장은 전체 성공하거나 뒤 권한 오류에서 전체 취소한다")
    void staffBatchIsAtomic() throws Exception {
        String created = create();
        long caseId = number(created, "$.businessCaseId"),
                requestId = number(created, "$.requests[0].securityRequestId");
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/reviews", requestId)
                        .with(authentication(auth(MemberRole.APPLICANT)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .hasStatus(201);
        String reviews = mvc.get()
                .uri("/api/security-requests/{id}/reviews", requestId)
                .with(authentication(auth(MemberRole.INFRA)))
                .exchange()
                .getResponse()
                .getContentAsString();
        List<Number> ids = JsonPath.read(reviews, "$[?(@.field=='INFRA')].reviewId");
        assertThat(
                        mvc.patch()
                                .uri("/api/reviews/{id}/answers", ids.getFirst())
                                .with(authentication(auth(MemberRole.INFRA)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"answers\":[{\"fieldKey\":\"network.source\",\"value\":\"192.0.2.1\",\"expectedRevision\":0},{\"fieldKey\":\"data.personal_info\",\"value\":\"false\"}]}"))
                .hasStatus(403);
        assertThat(mvc.get().uri("/api/business-cases/{id}", caseId).with(authentication(auth(MemberRole.APPLICANT))))
                .bodyJson()
                .extractingPath("$.caseAnswers[?(@.fieldKey=='network.source')].confirmed")
                .isEqualTo(List.of(false));
        assertThat(
                        mvc.patch()
                                .uri("/api/reviews/{id}/answers", ids.getFirst())
                                .with(authentication(auth(MemberRole.INFRA)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"answers\":[{\"fieldKey\":\"network.source\",\"value\":\"192.0.2.1\",\"expectedRevision\":0},{\"fieldKey\":\"network.destination\",\"value\":\"198.51.100.1\",\"expectedRevision\":0}]}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].valueSource")
                .isEqualTo(List.of("STAFF_INPUT", "STAFF_INPUT"));
    }

    private String create() throws Exception {
        MvcTestResult result = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(auth(MemberRole.APPLICANT)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"합성 도메인 경계 검증\",\"summary\":\"격리된 테스트 DB\",\"department\":\"합성부서\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        assertThat(result).hasStatus(201);
        return result.getResponse().getContentAsString();
    }

    private MvcTestResult put(String path, MemberRole role, String json) {
        return mvc.put()
                .uri(path)
                .with(authentication(auth(role)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static long number(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private static Authentication auth(MemberRole role) {
        String username = role == MemberRole.APPLICANT ? "applicant" : role == MemberRole.INFRA ? "infra" : "security";
        return new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(username, "합성 검증자", role),
                null,
                List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
