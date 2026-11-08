package kr.securityadvisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * OpenAPI 계약 파일 추출: 실행 중인 컨트롤러에서 springdoc 이 만든 문서를 docs/api/openapi.yaml 로 쓴다.
 * 실행: .\gradlew.bat test --tests "*OpenApiExportTest*" -PexportOpenApi=true  (계약 변경 PR 마다 갱신해 커밋한다 — Back 이 계약을 소유)
 */
@Tag("docker")
@EnabledIfSystemProperty(named = "openapi.export", matches = "true")
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiExportTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("[export] /v3/api-docs.yaml → docs/api/openapi.yaml")
    void export() throws Exception {
        MvcTestResult result = mvc.get().uri("/v3/api-docs.yaml").exchange();
        assertThat(result).hasStatusOk();
        String yaml = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(yaml).contains("openapi:").contains("/api/security-requests/{id}/submissions");
        Path target = Path.of("docs", "api", "openapi.yaml");
        Files.createDirectories(target.getParent());
        Files.writeString(target, yaml, StandardCharsets.UTF_8);
        System.out.println("[export] wrote " + target.toAbsolutePath() + " (" + yaml.length() + " chars)");
    }
}
