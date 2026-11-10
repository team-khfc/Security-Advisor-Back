package kr.securityadvisor.llm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 이전 이력을 복원하려는 명시적 요청. */
public record LlmRestoreCommand(
        @NotNull Long historyId,
        @NotNull Long expectedRevision,
        @NotBlank @Size(max = 500) String reason) {}
