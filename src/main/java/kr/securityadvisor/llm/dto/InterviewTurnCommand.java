package kr.securityadvisor.llm.dto;

import jakarta.validation.constraints.*;

/** 질문과 미저장 초안은 참고 입력이며 신청서 답변으로 자동 저장되지 않는다. */
public record InterviewTurnCommand(
        @NotBlank @Size(max = 100) String fieldKey,
        @NotBlank @Pattern(regexp = "ASK|SUGGEST") String mode,
        @NotBlank @Size(max = 4000) String text,
        @Size(max = 4000) String draft,
        @NotNull @PositiveOrZero Long expectedRevision) {}
