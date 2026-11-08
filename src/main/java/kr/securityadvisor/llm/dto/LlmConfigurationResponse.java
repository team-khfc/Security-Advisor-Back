package kr.securityadvisor.llm.dto;

import java.math.BigDecimal;
import java.util.List;
import kr.securityadvisor.llm.domain.LlmConfiguration;
import kr.securityadvisor.llm.domain.LlmProvider;

/**
 * LLM 구성 응답. 키 값은 절대 포함하지 않는다 — 환경변수가 설정돼 있는지(keyConfigured)만 알려 준다.
 */
public record LlmConfigurationResponse(
        Long llmConfigurationId,
        String name,
        LlmProvider provider,
        String baseUrl,
        String model,
        String apiKeyEnv,
        boolean keyConfigured,
        List<String> purposes,
        BigDecimal temperature,
        int maxTokens,
        int timeoutSeconds,
        int priority,
        boolean active,
        long revision) {

    public static LlmConfigurationResponse from(LlmConfiguration c, List<String> purposes, boolean keyConfigured) {
        return new LlmConfigurationResponse(
                c.getId(),
                c.getName(),
                c.getProvider(),
                c.getBaseUrl(),
                c.getModel(),
                c.getApiKeyEnv(),
                keyConfigured,
                purposes,
                c.getTemperature(),
                c.getMaxTokens(),
                c.getTimeoutSeconds(),
                c.getPriority(),
                c.isActive(),
                c.getRevision());
    }
}
