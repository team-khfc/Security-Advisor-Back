package kr.securityadvisor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 계정 활성/비활성 설정(기관 관리자). 비활성 계정은 로그인할 수 없고 자동 배정에서 빠진다. 삭제하지 않아 이력이 남는다.
 *
 * @param active 설정할 활성 여부
 * @param reason 사유(필수)
 */
public record ActivationCommand(
        boolean active, @NotBlank @Size(max = 1000) String reason) {}
