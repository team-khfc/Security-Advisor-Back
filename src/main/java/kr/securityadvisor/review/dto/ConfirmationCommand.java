package kr.securityadvisor.review.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 확정(PUT /api/submissions/{id}/confirmation). 배정된 SECURITY 담당자만, 두 분야 최종 확인 이후.
 *
 * @param expectedRevision 신청의 revision
 */
public record ConfirmationCommand(@NotNull Long expectedRevision) {}
