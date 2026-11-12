package kr.securityadvisor.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import kr.securityadvisor.policy.domain.PeriodicReviewDecision;

/** 담당자가 현재 규칙을 재검토한 판단과 사유. 기준 날짜는 서버가 결정한다. */
public record PeriodicReviewCommand(
        @NotNull PeriodicReviewDecision decision,
        @NotBlank @Size(max = 2000) String reason,
        @PositiveOrZero long expectedRevision) {}
