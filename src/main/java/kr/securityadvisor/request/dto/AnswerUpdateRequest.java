package kr.securityadvisor.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사용자 답변 저장 요청(필드 단위, PUT — 같은 값을 다시 보내도 결과가 같다).
 *
 * @param value 답변 값
 * @param expectedRevision 기존 답변의 revision. 처음 답하는 항목이면 null
 */
public record AnswerUpdateRequest(
        @NotBlank @Size(max = 4000) String value, Long expectedRevision) {}
