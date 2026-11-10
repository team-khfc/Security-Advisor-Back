package kr.securityadvisor.llm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import kr.securityadvisor.llm.domain.LlmProvider;
import kr.securityadvisor.llm.domain.LlmPurpose;

/** LLM 구성 생성·수정(관리자). apiKeyEnv 는 환경변수 이름이지 키 값이 아니다. */
public record LlmConfigurationCommand(
        @NotBlank @Size(max = 100) String name,
        @NotNull LlmProvider provider,
        @NotBlank @Size(max = 300) String baseUrl,
        @NotBlank @Size(max = 100) String model,
        @NotBlank @Size(max = 100) String apiKeyEnv,
        @NotEmpty List<LlmPurpose> purposes,
        BigDecimal temperature,
        Integer maxTokens,
        Integer timeoutSeconds,
        Integer priority,
        Boolean active,
        Long expectedRevision,
        @Size(max = 500) String reason) {}
