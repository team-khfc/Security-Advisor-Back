package kr.securityadvisor.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신청자의 보완 답변(POST /api/supplements/{id}/answers). 항목 값은 답변 API 로 먼저 고치고, 여기서는 메모와 함께 답변 완료를 알린다.
 *
 * @param note 보완 메모(선택)
 * @param expectedRevision 보완 요청의 revision
 */
public record SupplementAnswerCommand(
        @Size(max = 2000) String note, @NotNull Long expectedRevision) {}
