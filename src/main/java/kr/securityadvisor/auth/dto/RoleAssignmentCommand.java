package kr.securityadvisor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.securityadvisor.common.security.MemberRole;

/**
 * 역할·활성 상태를 한 번에 바꾸는 명령(관리자 화면용 — `PUT …/role` + `PUT …/activation` 을 합친 것).
 *
 * @param role 새 역할
 * @param active 활성 여부(null 이면 그대로)
 * @param reason 사유(필수)
 */
public record RoleAssignmentCommand(
        @NotNull MemberRole role,
        Boolean active,
        @NotBlank @Size(max = 1000) String reason) {}
