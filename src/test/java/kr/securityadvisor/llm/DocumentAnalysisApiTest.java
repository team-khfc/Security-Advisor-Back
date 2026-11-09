package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** F-02(fake 모드): 업로드(형식·소유 검사) → 분석 요청 202 → 비동기 요약 → 확인 필요(미확정). */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(
        properties = {
            "securityadvisor.demo.account-password=Test-Demo-1234",
            "securityadvisor.llm.mode=fake",
            "securityadvisor.storage.dir=${java.io.tmpdir}/securityadvisor-test-storage"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DocumentAnalysisApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication APPLICANT2 = auth("applicant2", "이신청", MemberRole.APPLICANT);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("txt 업로드 → 분석 요청(202) → 요약 완료는 '확인 필요' 상태. exe 는 422, 남의 사업 건은 404")
    void uploadAndAnalyze() throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"업로드 테스트\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        Long caseId =
                ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.businessCaseId")).longValue();

        MockMultipartFile plan = new MockMultipartFile(
                "file",
                "사업계획서(합성).txt",
                "text/plain",
                "민원 포털이 외부 공개 API 를 HTTPS 443 으로 호출한다. 개인정보는 처리하지 않는다. (합성 문서)".getBytes(StandardCharsets.UTF_8));
        assertThat(mvc.post()
                        .uri("/api/business-cases/{id}/uploaded-files", caseId)
                        .multipart()
                        .file(plan)
                        .with(authentication(APPLICANT2))
                        .with(csrf()))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post()
                        .uri("/api/business-cases/{id}/uploaded-files", caseId)
                        .multipart()
                        .file(new MockMultipartFile(
                                "file", "virus.exe", "application/octet-stream", new byte[] {1, 2, 3}))
                        .with(authentication(APPLICANT))
                        .with(csrf()))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        // 이름만 .pdf 인 실행 파일(내용 서명 불일치) 422, 내용이 OLE 가 아닌 .hwp 도 422
        assertThat(mvc.post()
                        .uri("/api/business-cases/{id}/uploaded-files", caseId)
                        .multipart()
                        .file(new MockMultipartFile(
                                "file", "fake.pdf", "application/pdf", new byte[] {0x4D, 0x5A, (byte) 0x90, 0, 3}))
                        .with(authentication(APPLICANT))
                        .with(csrf()))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        MvcTestResult hwp = mvc.post()
                .uri("/api/business-cases/{id}/uploaded-files", caseId)
                .multipart()
                .file(new MockMultipartFile("file", "계획서.hwp", "application/x-hwp", new byte[] {1, 2, 3}))
                .with(authentication(APPLICANT))
                .with(csrf())
                .exchange();
        assertThat(hwp).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(hwp.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("HWP 5.0");
        MvcTestResult uploaded = mvc.post()
                .uri("/api/business-cases/{id}/uploaded-files", caseId)
                .multipart()
                .file(plan)
                .with(authentication(APPLICANT))
                .with(csrf())
                .exchange();
        assertThat(uploaded).hasStatus(HttpStatus.CREATED);
        assertThat(uploaded).bodyJson().extractingPath("$.originalName").isEqualTo("사업계획서(합성).txt");
        Long fileId =
                ((Number) JsonPath.read(uploaded.getResponse().getContentAsString(), "$.uploadedFileId")).longValue();

        MvcTestResult requested = mvc.post()
                .uri("/api/business-cases/{id}/analysis-jobs", caseId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadedFileId\":" + fileId + "}")
                .exchange();
        assertThat(requested).hasStatus(HttpStatus.ACCEPTED);
        assertThat(requested).headers().containsHeader("Location");
        assertThat(requested).bodyJson().extractingPath("$.status").isEqualTo("QUEUED");
        Long jobId =
                ((Number) JsonPath.read(requested.getResponse().getContentAsString(), "$.analysisJobId")).longValue();

        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    MvcTestResult job = mvc.get()
                            .uri("/api/analysis-jobs/{id}", jobId)
                            .with(authentication(APPLICANT))
                            .exchange();
                    assertThat(job).hasStatusOk();
                    assertThat(job).bodyJson().extractingPath("$.status").isEqualTo("NEEDS_REVIEW");
                });
        MvcTestResult job = mvc.get()
                .uri("/api/analysis-jobs/{id}", jobId)
                .with(authentication(APPLICANT))
                .exchange();
        assertThat(job).bodyJson().extractingPath("$.summary").asString().contains("합성 요약");
        assertThat(job).bodyJson().extractingPath("$.modelReference").isEqualTo("fake/offline");
        assertThat(mvc.get()
                        .uri("/api/business-cases/{id}/uploaded-files", caseId)
                        .with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.length()")
                .isEqualTo(1);

        // 분석 결과 적용: 요약이 사업 개요 AI 제안(미확정)으로 들어가고 미제출 신청에 AI 항목 제안이 실행된다. revision 틀리면 409
        long caseRevision = ((Number) JsonPath.read(
                        mvc.get()
                                .uri("/api/business-cases/{id}", caseId)
                                .with(authentication(APPLICANT))
                                .exchange()
                                .getResponse()
                                .getContentAsString(),
                        "$.revision"))
                .longValue();
        assertThat(mvc.post()
                        .uri("/api/analysis-jobs/{id}/applications", jobId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + (caseRevision + 99) + "}"))
                .hasStatus(HttpStatus.CONFLICT);
        MvcTestResult applied = mvc.post()
                .uri("/api/analysis-jobs/{id}/applications", jobId)
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":" + caseRevision + "}")
                .exchange();
        assertThat(applied).hasStatus(HttpStatus.CREATED);
        // 사업 개요는 사용자가 확인한 값이 있으므로 덮어쓰지 않고 경고만 남긴다(불변 원칙 1).
        assertThat(applied).bodyJson().extractingPath("$.summaryProposal").isNull();
        assertThat(applied).bodyJson().extractingPath("$.warnings").asArray().hasSize(1);
        assertThat(applied).bodyJson().extractingPath("$.proposals").asArray().hasSize(1);
        assertThat(applied)
                .bodyJson()
                .extractingPath("$.proposals[0].result.modelReference")
                .isEqualTo("fake/offline");
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
