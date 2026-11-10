package kr.securityadvisor.llm.dto;

import jakarta.validation.constraints.*;

/** 담당자 설명은 제안용 자료이며 기존 답변을 변경하지 않는다. */
public record InfrastructureProposalCommand(
        @NotNull @PositiveOrZero Long expectedRevision,
        @NotNull @Size(max = 6000) String explanation) {}
