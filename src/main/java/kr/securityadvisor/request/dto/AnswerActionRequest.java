package kr.securityadvisor.request.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 답변에 대한 확인·담당자 확인 요청 등 값 없는 행위 요청.
 *
 * @param expectedRevision 답변의 revision — 다르면 409
 */
public record AnswerActionRequest(@NotNull Long expectedRevision) {}
