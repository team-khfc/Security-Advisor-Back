package kr.securityadvisor.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신청 철회 요청(F-05). 사유 필수(불변식 13).
 *
 * @param reason 철회 사유
 * @param expectedRevision 신청의 revision — 다르면 409
 */
public record WithdrawRequest(@NotBlank @Size(max = 1000) String reason, @NotNull Long expectedRevision) {}
