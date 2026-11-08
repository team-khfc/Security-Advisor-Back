package kr.securityadvisor.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 담당자 목록 활성/비활성 설정(PUT …/activation). 비활성화 사유는 감사 이력에 남는다.
 *
 * @param active 설정할 활성 여부
 * @param reason 사유(비활성화 시 필수)
 */
public record ReviewerPoolActivationCommand(
        boolean active, @NotBlank @Size(max = 1000) String reason) {}
