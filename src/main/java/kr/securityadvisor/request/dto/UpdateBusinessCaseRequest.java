package kr.securityadvisor.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 사업 건 개요 수정 요청.
 *
 * @param expectedRevision 화면이 읽었던 revision — 다르면 409
 */
public record UpdateBusinessCaseRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String summary,
        @Size(max = 100) String department,
        @NotNull Long expectedRevision) {}
