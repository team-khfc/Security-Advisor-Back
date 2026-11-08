package kr.securityadvisor.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.securityadvisor.common.schema.ReviewField;

/**
 * 담당자 목록 등록(기관 관리자). 계정은 활성이고 분야에 맞는 역할이어야 한다.
 *
 * @param defaultReviewer true 면 등록과 동시에 그 분야의 기본 담당자로 지정(이전 기본은 해제)
 */
public record ReviewerPoolRegisterCommand(
        @NotNull ReviewField field,
        @NotBlank @Size(max = 50) String memberUsername,
        boolean defaultReviewer) {}
