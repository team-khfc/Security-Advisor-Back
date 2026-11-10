package kr.securityadvisor.llm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 저장되지 않은 입력으로도 연결만 시험한다. 시험 성공은 구성 적용이 아니다. */
public record LlmConnectionTestCommand(
        Long configurationId,
        Long expectedRevision,
        @NotNull @Valid LlmConfigurationCommand configuration) {}
