package kr.securityadvisor.llm.dto;

import java.math.BigDecimal;
import java.util.List;
import kr.securityadvisor.llm.domain.LlmConfiguration;
import kr.securityadvisor.llm.domain.LlmProvider;
import kr.securityadvisor.llm.domain.LlmPurpose;

/** 구성 비교·이력의 불변 값. 키 값이나 실행 시점의 키 설정 여부는 보존하지 않는다. */
public record LlmConfigurationValues(
        String name,
        LlmProvider provider,
        String baseUrl,
        String model,
        String apiKeyEnv,
        List<String> purposes,
        BigDecimal temperature,
        int maxTokens,
        int timeoutSeconds,
        int priority,
        boolean active) {
    public static LlmConfigurationValues from(LlmConfiguration c, List<String> purposes) {
        return new LlmConfigurationValues(
                c.getName(),
                c.getProvider(),
                c.getBaseUrl(),
                c.getModel(),
                c.getApiKeyEnv(),
                purposes,
                c.getTemperature(),
                c.getMaxTokens(),
                c.getTimeoutSeconds(),
                c.getPriority(),
                c.isActive());
    }

    /** 과거 값도 현재 검증을 다시 거쳐 새 변경으로 저장한다. */
    public LlmConfigurationCommand command(long revision, String reason) {
        return new LlmConfigurationCommand(
                name,
                provider,
                baseUrl,
                model,
                apiKeyEnv,
                purposes.stream().map(LlmPurpose::valueOf).toList(),
                temperature,
                maxTokens,
                timeoutSeconds,
                priority,
                active,
                revision,
                reason);
    }
}
