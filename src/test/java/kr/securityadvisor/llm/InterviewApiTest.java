package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.llm.service.LlmGateway;
import kr.securityadvisor.llm.service.LlmResult;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@Tag("docker")
@SpringBootTest(properties = {"securityadvisor.demo.account-password=Test-Demo-1234", "securityadvisor.llm.mode=fake"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InterviewApiTest {
    @Autowired
    private MockMvcTester mvc;

    @MockitoSpyBean
    private LlmGateway gateway;

    private static final Authentication APPLICANT = auth("applicant", MemberRole.APPLICANT);

    private static Authentication auth(String username, MemberRole role) {
        return new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(username, "합성 사용자", role),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private long create() throws Exception {
        MvcTestResult result = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"합성 인터뷰 시험\",\"summary\":\"\",\"department\":\"합성 부서\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.businessCaseId")).longValue();
    }

    private MvcTestResult ask(long id, String mode, String field, Authentication actor) {
        return mvc.post()
                .uri("/api/business-cases/{id}/interview-turns", id)
                .with(authentication(actor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"" + mode + "\",\"fieldKey\":\"" + field
                        + "\",\"text\":\"쉽게 설명해 주세요\",\"expectedRevision\":0}")
                .exchange();
    }

    private long turnId(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.interviewTurnId")).longValue();
    }

    private MvcTestResult apply(long id, long turn) {
        return mvc.post()
                .uri("/api/business-cases/{id}/interview-turns/{turn}/applications", id, turn)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0}")
                .exchange();
    }

    private String caseBody(long id) throws Exception {
        return mvc.get()
                .uri("/api/business-cases/{id}", id)
                .with(authentication(APPLICANT))
                .exchange()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @DisplayName("자유 질문과 추천 미리보기는 답변을 변경하지 않고 적용 후에만 AI 미확정 답변이 된다")
    void previewApplyConfirm() throws Exception {
        long id = create();
        String before = caseBody(id);
        long explanation = turnId(ask(id, "ASK", "project.summary", APPLICANT));
        assertThat(caseBody(id)).isEqualTo(before);
        assertThat(apply(id, explanation)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        long proposal = turnId(ask(id, "SUGGEST", "project.summary", APPLICANT));
        assertThat(caseBody(id)).isEqualTo(before);
        assertThat(apply(id, proposal)).hasStatusOk();
        List<String> sources =
                JsonPath.read(caseBody(id), "$.caseAnswers[?(@.fieldKey=='project.summary')].valueSource");
        List<Boolean> confirmations =
                JsonPath.read(caseBody(id), "$.caseAnswers[?(@.fieldKey=='project.summary')].confirmed");
        assertThat(sources).containsExactly("AI_INFERRED");
        assertThat(confirmations).containsExactly(false);
        assertThat(apply(id, proposal)).hasStatusOk();
        List<Number> revisions =
                JsonPath.read(caseBody(id), "$.caseAnswers[?(@.fieldKey=='project.summary')].revision");
        assertThat(mvc.put()
                        .uri("/api/business-cases/{id}/answers/project.summary/confirmation", id)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revisions.getFirst() + "}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.confirmed")
                .isEqualTo(true);
        long newProposal = turnId(ask(id, "SUGGEST", "project.summary", APPLICANT));
        assertThat(apply(id, newProposal)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mvc.get().uri("/api/business-cases/{id}/interview-turns", id).with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.length()")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("신청자 소유권과 게시본 범위·담당자 항목 제한을 지킨다")
    void authorizationAndFields() throws Exception {
        long id = create();
        assertThat(ask(id, "ASK", "project.summary", auth("applicant2", MemberRole.APPLICANT)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(ask(id, "ASK", "project.summary", auth("infra", MemberRole.INFRA)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get()
                        .uri("/api/business-cases/{id}/interview-turns", id)
                        .with(authentication(auth("applicant2", MemberRole.APPLICANT))))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(ask(id, "SUGGEST", "network.source", APPLICANT)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ask(id, "SUGGEST", "missing.field", APPLICANT)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(ask(id, "SUGGEST", "firewall.purpose", APPLICANT)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("추천 생성 이후 다른 항목이 변경되면 적용을 거부하고 사용자가 저장한 값을 보존한다")
    void staleProposal() throws Exception {
        long id = create();
        long proposal = turnId(ask(id, "SUGGEST", "project.summary", APPLICANT));
        assertThat(mvc.put()
                        .uri("/api/business-cases/{id}/answers/data.personal_info/staff-check", id)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .hasStatusOk();
        String changed = caseBody(id);
        assertThat(apply(id, proposal)).hasStatus(HttpStatus.CONFLICT);
        assertThat(caseBody(id)).isEqualTo(changed);
    }

    @Test
    @DisplayName("없는 출처·추측 IP·잘못된 입력 형식은 적용 가능한 후보로 노출하지 않는다")
    void rejectUnbackedOutput() throws Exception {
        long id = create();
        String before = caseBody(id);
        for (String output : List.of(
                "{\"reply\":\"설명\",\"proposedValue\":\"예\",\"evidence\":[\"S9999999\"]}",
                "{\"reply\":\"서버는 192.0.2.9\",\"proposedValue\":\"예\",\"evidence\":[\"F\"]}",
                "{\"reply\":\"설명\",\"proposedValue\":\"확인 안 됨\",\"evidence\":[\"F\"]}")) {
            doReturn(new LlmResult(output, "fake/test")).when(gateway).complete(any(), any(), any());
            MvcTestResult result = ask(id, "SUGGEST", "data.personal_info", APPLICANT);
            long turn = turnId(result);
            assertThat(result).bodyJson().extractingPath("$.flagged").isEqualTo(true);
            assertThat(result).bodyJson().extractingPath("$.proposedValue").isNull();
            assertThat(apply(id, turn)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        }
        assertThat(caseBody(id)).isEqualTo(before);
        doReturn(new LlmResult("잘못된 형식", "fake/test")).when(gateway).complete(any(), any(), any());
        assertThat(ask(id, "ASK", "project.summary", APPLICANT)).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
