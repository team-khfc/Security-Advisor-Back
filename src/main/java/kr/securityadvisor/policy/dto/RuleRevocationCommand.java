package kr.securityadvisor.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 회수(PUT /api/firewall-rules/{id}/revocation). 사유 필수. */
public record RuleRevocationCommand(
        @NotBlank @Size(max = 2000) String reason, @NotNull Long expectedRevision) {}
