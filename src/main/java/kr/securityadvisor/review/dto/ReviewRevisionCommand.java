package kr.securityadvisor.review.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 협의에 대한 값 없는 행위(할당 완료 등).
 *
 * @param expectedRevision 협의의 revision — 다르면 409
 */
public record ReviewRevisionCommand(@NotNull Long expectedRevision) {}
