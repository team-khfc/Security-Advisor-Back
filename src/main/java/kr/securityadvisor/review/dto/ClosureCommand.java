package kr.securityadvisor.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 종결(반려) 요청(PUT …/closure). 사유 필수, 이후 수정 불가(불변식 13).
 *
 * @param expectedRevision 신청의 revision
 */
public record ClosureCommand(
        @NotBlank @Size(max = 2000) String reason, @NotNull Long expectedRevision) {}
