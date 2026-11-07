package kr.securityadvisor.request.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 제출 요청(POST /api/security-requests/{id}/submissions). 멱등성 키는 {@code Idempotency-Key} 헤더로 받는다.
 *
 * @param expectedRevision 신청의 revision — 다르면 409
 */
public record SubmitCommand(@NotNull Long expectedRevision) {}
