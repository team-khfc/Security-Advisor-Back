package kr.securityadvisor.policy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 적용 기록(PUT /api/firewall-rules/{id}/application). INFRA 가 사람으로서 기록한다.
 *
 * @param result APPLIED(적용 완료) 또는 NOT_APPLICABLE(적용 불가 — reason 필수)
 */
public record RuleApplicationCommand(
        @NotNull Result result,
        @Size(max = 2000) String note,
        @Size(max = 2000) String reason,
        @NotNull Long expectedRevision) {

    public enum Result {
        APPLIED,
        NOT_APPLICABLE
    }
}
