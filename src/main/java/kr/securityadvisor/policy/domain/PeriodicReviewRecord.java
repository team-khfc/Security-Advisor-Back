package kr.securityadvisor.policy.domain;

import java.time.Instant;
import java.time.LocalDate;

/** 규칙별 추가 전용 재검토 이력. */
public record PeriodicReviewRecord(
        PeriodicReviewDecision decision,
        String reason,
        String reviewedBy,
        Instant reviewedAt,
        LocalDate previousReviewDate,
        LocalDate nextReviewDate) {}
