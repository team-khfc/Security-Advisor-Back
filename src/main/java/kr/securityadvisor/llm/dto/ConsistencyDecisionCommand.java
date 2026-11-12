package kr.securityadvisor.llm.dto;

import jakarta.validation.constraints.*;

public record ConsistencyDecisionCommand(
        @NotNull Long expectedRevision,
        @NotBlank String expectedFingerprint,
        @Min(0) int findingIndex,
        @NotBlank String status,
        @NotBlank @Size(max = 2000) String reason) {}
