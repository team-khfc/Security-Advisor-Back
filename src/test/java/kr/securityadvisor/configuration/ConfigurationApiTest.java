package kr.securityadvisor.configuration;

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
import tools.jackson.databind.ObjectMapper;

/** F-08: 초안은 현재 게시본에서 시작, 수정 저장(검증) → 게시하면 새 버전. 기존 사업 건의 게시본은 유지. 관리자만. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConfigurationApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication ADMIN = auth("admin", "최관리", MemberRole.ADMIN);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("초안 조회 → 서류 유형 이름 수정·항목 추가 → 게시 v2 → 새 사업 건은 v2, 이전 사업 건은 v1 유지")
    void draftAndPublish() throws Exception {
        MvcTestResult oldCase = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"게시 전 사업\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        Long oldCaseId =
                ((Number) JsonPath.read(oldCase.getResponse().getContentAsString(), "$.businessCaseId")).longValue();
        MvcTestResult beforeDetail = mvc.get()
                .uri("/api/business-cases/{id}", oldCaseId)
                .with(authentication(APPLICANT))
                .exchange();
        long oldPublicationId = ((Number)
                        JsonPath.read(beforeDetail.getResponse().getContentAsString(), "$.publicationId"))
                .longValue();

        assertThat(mvc.get().uri("/api/configurations/current").with(authentication(APPLICANT)))
                .hasStatus(HttpStatus.FORBIDDEN);
        MvcTestResult current = mvc.get()
                .uri("/api/configurations/current")
                .with(authentication(ADMIN))
                .exchange();
        assertThat(current).hasStatusOk();
        String body = current.getResponse().getContentAsString();
        Long configurationId = ((Number) JsonPath.read(body, "$.configurationId")).longValue();
        int basedOn = JsonPath.read(body, "$.basedOnVersion");
        long revision = ((Number) JsonPath.read(body, "$.revision")).longValue();

        // 초안 수정: 서류 이름 변경 + 항목 추가(합성). 중복 키는 422
        String draft = new ObjectMapper().writeValueAsString(JsonPath.read(body, "$.draft"));
        String modified = draft.replace("방화벽 정책 허용 신청서", "방화벽 정책 허용 신청서(v2)")
                .replaceFirst(
                        "\"fields\":\\[",
                        "\"fields\":[{\"key\":\"project.contact\",\"label\":\"담당자 연락처(합성)\",\"scope\":\"CASE\",\"docTypes\":[],\"type\":\"TEXT\",\"required\":false,\"askRole\":\"APPLICANT\",\"hint\":null},");
        assertThat(mvc.put()
                        .uri("/api/configurations/{id}", configurationId)
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"draft\":"
                                + modified.replaceFirst(
                                        "\"fields\":\\[",
                                        "\"fields\":[{\"key\":\"project.contact\",\"label\":\"중복\",\"scope\":\"CASE\",\"docTypes\":[],\"type\":\"TEXT\",\"required\":false,\"askRole\":\"APPLICANT\",\"hint\":null},")
                                + ",\"note\":\"중복 시도\",\"expectedRevision\":" + revision + "}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        MvcTestResult saved = mvc.put()
                .uri("/api/configurations/{id}", configurationId)
                .with(authentication(ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"draft\":" + modified + ",\"note\":\"v2 초안\",\"expectedRevision\":" + revision + "}")
                .exchange();
        assertThat(saved).hasStatusOk();
        assertThat(saved).bodyJson().extractingPath("$.draft.fields[0].key").isEqualTo("project.contact");
        long savedRevision =
                ((Number) JsonPath.read(saved.getResponse().getContentAsString(), "$.revision")).longValue();

        MvcTestResult published = mvc.post()
                .uri("/api/configurations/{id}/publications", configurationId)
                .with(authentication(ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":" + savedRevision + "}")
                .exchange();
        assertThat(published).hasStatus(HttpStatus.CREATED);
        assertThat(published).bodyJson().extractingPath("$.version").isEqualTo(basedOn + 1);
        assertThat(mvc.get().uri("/api/publications/current").with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.version")
                .isEqualTo(basedOn + 1);

        // 새 사업 건은 새 게시본, 이전 사업 건은 생성 시점 게시본 유지(불변식 8)
        MvcTestResult newCase = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"게시 후 사업\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        Long newCaseId =
                ((Number) JsonPath.read(newCase.getResponse().getContentAsString(), "$.businessCaseId")).longValue();
        MvcTestResult afterNew = mvc.get()
                .uri("/api/business-cases/{id}", newCaseId)
                .with(authentication(APPLICANT))
                .exchange();
        long newPublicationId =
                ((Number) JsonPath.read(afterNew.getResponse().getContentAsString(), "$.publicationId")).longValue();
        assertThat(newPublicationId).isNotEqualTo(oldPublicationId);
        MvcTestResult afterOld = mvc.get()
                .uri("/api/business-cases/{id}", oldCaseId)
                .with(authentication(APPLICANT))
                .exchange();
        assertThat(((Number) JsonPath.read(afterOld.getResponse().getContentAsString(), "$.publicationId")).longValue())
                .isEqualTo(oldPublicationId);
        List<String> newCaseFields =
                JsonPath.read(afterNew.getResponse().getContentAsString(), "$.caseAnswers[*].fieldKey");
        assertThat(newCaseFields).isNotNull();
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
