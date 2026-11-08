package kr.securityadvisor.llm.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 계층 설정(application.yml {@code securityadvisor.llm}).
 *
 * @param mode live(실제 호출) / fake(고정 응답)
 * @param allowedHosts 엔드포인트 허용 호스트(쉼표 구분)
 */
@ConfigurationProperties(prefix = "securityadvisor.llm")
public record LlmProperties(String mode, String allowedHosts) {

    public boolean isFake() {
        return "fake".equalsIgnoreCase(mode);
    }

    public Set<String> allowedHostSet() {
        if (allowedHosts == null || allowedHosts.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedHosts.split(","))
                .map(String::strip)
                .filter(h -> !h.isEmpty())
                .map(h -> h.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
