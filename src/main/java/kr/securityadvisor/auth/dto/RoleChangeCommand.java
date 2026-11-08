package kr.securityadvisor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.securityadvisor.common.security.MemberRole;

/**
 * 역할 변경 명령(기관 관리자, F-11). 역할은 4종 중 하나이며 사유는 감사 이력에 남는다.
 *
 * @param role 새 역할
 * @param reason 사유(필수)
 */
public record RoleChangeCommand(
        @NotNull MemberRole role,
        @NotBlank @Size(max = 1000) String reason) {}
