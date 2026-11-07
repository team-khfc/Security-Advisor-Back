package kr.securityadvisor.review.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 협의 요청(1→2단계) 본문.
 *
 * @param expectedRevision 신청의 revision — 다르면 409
 */
public record ReviewRequestCommand(@NotNull Long expectedRevision) {}
