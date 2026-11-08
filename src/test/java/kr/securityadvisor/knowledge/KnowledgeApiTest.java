package kr.securityadvisor.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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

/** F-09 1차: seed 스니펫 전문검색, 비공개 컨텍스트의 역할별 가시성, 관리자 등록 규칙. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = {"securityadvisor.demo.account-password=Test-Demo-1234", "securityadvisor.llm.mode=fake"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class KnowledgeApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication INFRA = auth("infra", "박인프라", MemberRole.INFRA);
    private static final Authentication ADMIN = auth("admin", "최관리", MemberRole.ADMIN);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("전문검색은 seed 스니펫을 출처와 함께 찾고, 검색식 문자는 무해화된다")
    void fullTextSearch() {
        assertThat(mvc.get().uri("/api/knowledge-snippets/search?q=허용 기간 재검토일").with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].citation")
                .asString()
                .contains("3장 2절");
        assertThat(mvc.get().uri("/api/knowledge-snippets/search?q=')) & | !(*").with(authentication(APPLICANT)))
                .hasStatusOk();
    }

    @Test
    @DisplayName("비공개 컨텍스트는 신청자에게 비어 있고 담당자에게는 보인다. APPLICANT 에게 보이는 행은 등록할 수 없다")
    void privateContextVisibility() {
        assertThat(mvc.get().uri("/api/private-contexts/suggestions?q=업무서버").with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .isEmpty();
        assertThat(mvc.get().uri("/api/private-contexts/suggestions?q=WAS-01").with(authentication(INFRA)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .isEqualTo(List.of("합성 업무서버 01"));
        assertThat(mvc.get().uri("/api/private-contexts").with(authentication(INFRA)))
                .hasStatus(HttpStatus.FORBIDDEN);

        String body = "{\"kind\":\"SERVER\",\"name\":\"합성 서버 X\",\"aliases\":[],\"attributes\":{\"ip\":\"10.9.9.9\"},"
                + "\"visibleRoles\":[\"APPLICANT\"]}";
        assertThat(mvc.post()
                        .uri("/api/private-contexts")
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mvc.post()
                        .uri("/api/private-contexts")
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("APPLICANT", "INFRA")))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("LLM 구성 목록은 관리자만 보고 키 값은 응답에 없다. 허용 목록 밖 호스트는 등록 불가")
    void llmConfigurations() {
        assertThat(mvc.get().uri("/api/llm-configurations").with(authentication(INFRA)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/llm-configurations").with(authentication(ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .isEqualTo(List.of("openai-default", "ollama-exaone"));
        assertThat(mvc.post()
                        .uri("/api/llm-configurations")
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"bad\",\"provider\":\"OPENAI_COMPATIBLE\",\"baseUrl\":\"https://evil.example.com\","
                                        + "\"model\":\"m\",\"apiKeyEnv\":\"EVIL_KEY\",\"purposes\":[\"INTERVIEW\"]}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
