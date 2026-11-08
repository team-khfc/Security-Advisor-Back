package kr.securityadvisor.policy.dto;

import java.time.Instant;
import java.time.LocalDate;
import kr.securityadvisor.policy.domain.FirewallRule;
import kr.securityadvisor.policy.domain.FirewallRuleStatus;

/**
 * 규칙 응답. status 는 저장 상태, effectiveStatus 는 오늘 기준 계산값(APPLIED 규칙의 종료일이 지나면 EXPIRED, 예고 일수 안이면 EXPIRING).
 */
public record FirewallRuleResponse(
        Long firewallRuleId,
        Long securityRequestId,
        Long submissionId,
        int ruleNo,
        String source,
        String destination,
        String protocol,
        String port,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate reviewDate,
        FirewallRuleStatus status,
        String statusLabel,
        String effectiveStatus,
        String effectiveStatusLabel,
        String appliedBy,
        Instant appliedAt,
        String applicationNote,
        String reason,
        Long renewalRequestId,
        long revision) {

    public static FirewallRuleResponse from(FirewallRule r, LocalDate today, int expiryNoticeDays) {
        String effective = r.getStatus().name();
        String effectiveLabel = r.getStatus().label();
        if (r.getStatus() == FirewallRuleStatus.APPLIED && r.getEndDate() != null) {
            if (r.getEndDate().isBefore(today)) {
                effective = "EXPIRED";
                effectiveLabel = "만료";
            } else if (!r.getEndDate().isAfter(today.plusDays(expiryNoticeDays))) {
                effective = "EXPIRING";
                effectiveLabel = "만료 예정";
            }
        }
        return new FirewallRuleResponse(
                r.getId(),
                r.getSecurityRequestId(),
                r.getSubmissionId(),
                r.getRuleNo(),
                r.getSource(),
                r.getDestination(),
                r.getProtocol(),
                r.getPort(),
                r.getStartDate(),
                r.getEndDate(),
                r.getReviewDate(),
                r.getStatus(),
                r.getStatus().label(),
                effective,
                effectiveLabel,
                r.getAppliedBy(),
                r.getAppliedAt(),
                r.getApplicationNote(),
                r.getReason(),
                r.getRenewalRequestId(),
                r.getRevision());
    }
}
