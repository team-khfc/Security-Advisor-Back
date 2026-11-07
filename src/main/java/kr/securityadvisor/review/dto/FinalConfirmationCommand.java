package kr.securityadvisor.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 분야 최종 확인(POST /api/submissions/{id}/final-confirmations). 분야는 호출자의 역할·배정으로 정해진다.
 *
 * @param opinion 확인 의견(선택)
 * @param expectedRevision 신청의 revision
 */
public record FinalConfirmationCommand(
        @Size(max = 4000) String opinion, @NotNull Long expectedRevision) {}
