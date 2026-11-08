package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.llm.domain.LlmConfiguration;
import kr.securityadvisor.llm.domain.LlmProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LLM 구성 불변식: 허용 호스트만, 키는 환경변수 이름만, 파라미터 범위. */
class LlmConfigurationTest {

    private static final Set<String> ALLOWED = Set.of("api.openai.com", "ollama");

    @Test
    @DisplayName("허용 목록에 없는 호스트·잘못된 URL 은 거부한다(SSRF 방지)")
    void hostAllowlist() {
        assertThatThrownBy(() -> create("https://evil.example.com", "OPENAI_API_KEY"))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("허용되지 않은");
        assertThatThrownBy(() -> create("ftp://api.openai.com", "OPENAI_API_KEY"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThat(create("https://api.openai.com", "OPENAI_API_KEY").reference())
                .isEqualTo("test/gpt-synthetic");
        assertThat(LlmConfiguration.hostOf("http://Ollama:11434/v1")).isEqualTo("ollama");
    }

    @Test
    @DisplayName("api_key_env 는 환경변수 이름 형식만 — 키 값처럼 보이는 문자열은 거부한다")
    void apiKeyEnvNameOnly() {
        assertThatThrownBy(() -> create("https://api.openai.com", "sk-abcdef0123456789"))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("환경변수 이름");
        assertThatThrownBy(() -> create("https://api.openai.com", "openai_key"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("temperature·maxTokens·timeout 범위를 벗어나면 거부한다")
    void parameterRanges() {
        assertThatThrownBy(() -> LlmConfiguration.create(
                        "t",
                        LlmProvider.OPENAI_COMPATIBLE,
                        "https://api.openai.com",
                        "m",
                        "OPENAI_API_KEY",
                        "[\"INTERVIEW\"]",
                        true,
                        new BigDecimal("2.5"),
                        1024,
                        60,
                        10,
                        ALLOWED))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> LlmConfiguration.create(
                        "t",
                        LlmProvider.OPENAI_COMPATIBLE,
                        "https://api.openai.com",
                        "m",
                        "OPENAI_API_KEY",
                        "[\"INTERVIEW\"]",
                        true,
                        new BigDecimal("0.2"),
                        10,
                        60,
                        10,
                        ALLOWED))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    private static LlmConfiguration create(String baseUrl, String apiKeyEnv) {
        return LlmConfiguration.create(
                "test",
                LlmProvider.OPENAI_COMPATIBLE,
                baseUrl,
                "gpt-synthetic",
                apiKeyEnv,
                "[\"INTERVIEW\"]",
                true,
                new BigDecimal("0.2"),
                1024,
                60,
                10,
                ALLOWED);
    }
}
