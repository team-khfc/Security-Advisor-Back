package kr.securityadvisor.policy.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.auth.service.MemberDirectory;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.notification.domain.NotificationKind;
import kr.securityadvisor.notification.service.Notifier;
import kr.securityadvisor.policy.domain.FirewallRuleStatus;
import kr.securityadvisor.policy.repository.FirewallRuleRepository;
import kr.securityadvisor.request.service.OperationalWork;
import kr.securityadvisor.review.dto.TodoItem;
import kr.securityadvisor.review.service.TodoContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서류 확정 후에도 운영 규칙의 재검토와 배정 공백을 추적한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FirewallOperationalWork implements OperationalWork, TodoContributor {
    private final FirewallRuleRepository rules;
    private final AssignmentService assignments;
    private final MemberDirectory members;
    private final Notifier notifier;
    private final Clock clock;

    @Override
    public boolean hasOngoingWork(Long requestId) {
        return rules.findBySecurityRequestIdOrderByRuleNoAsc(requestId).stream()
                .anyMatch(r -> r.getStatus() == FirewallRuleStatus.APPLIED
                        || r.getStatus() == FirewallRuleStatus.PENDING_APPLY);
    }

    @Override
    public List<TodoItem> items(MemberPrincipal member) {
        List<TodoItem> result = new ArrayList<>();
        LocalDate today = LocalDate.ofInstant(clock.instant(), FirewallRuleService.KOREA);
        for (var rule : rules.findAllByOrderByIdDesc()) {
            boolean active = rule.getStatus() == FirewallRuleStatus.APPLIED
                    || rule.getStatus() == FirewallRuleStatus.PENDING_APPLY;
            if (!active) continue;
            Long requestId = rule.getSecurityRequestId();
            var assignee = assignments.eligibleAssignee(requestId, ReviewField.INFRA);
            if (member.role() == MemberRole.ADMIN && assignee.isEmpty()) {
                var item = new TodoItem(
                        "REASSIGNMENT:" + requestId,
                        "REASSIGNMENT",
                        requestId,
                        "신청 #" + requestId + " · 방화벽 운영 담당자 재배정",
                        "/requests/" + requestId);
                if (!result.contains(item)) result.add(item);
            } else if (member.role() == MemberRole.INFRA
                    && assignee.filter(member.username()::equals).isPresent()
                    && rule.needsReview(today)) {
                result.add(new TodoItem(
                        "FIREWALL_REVIEW:" + rule.getId(),
                        "FIREWALL_REVIEW",
                        requestId,
                        "규칙 #" + rule.getId() + " · " + label(rule.reviewStatus(today)) + " (" + rule.getReviewDate()
                                + ")",
                        "/policies"));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public void remind(LocalDate asOf, Long onlyRequestId) {
        for (var rule : rules.findAllByOrderByIdDesc()) {
            if (onlyRequestId != null && !onlyRequestId.equals(rule.getSecurityRequestId())) continue;
            if (!rule.needsReview(asOf)) continue;
            Long requestId = rule.getSecurityRequestId();
            var assignee = assignments.eligibleAssignee(requestId, ReviewField.INFRA);
            String key = "firewall-review:" + rule.getId() + ":" + rule.getReviewDate() + ":"
                    + rule.getReviewHistory().size();
            if (assignee.isPresent()) {
                notifier.notify(
                        assignee.get(),
                        key + ":" + rule.reviewStatus(asOf),
                        NotificationKind.FIREWALL_REVIEW,
                        "신청 #" + requestId + " 규칙 #" + rule.getId() + " · " + label(rule.reviewStatus(asOf)) + " ("
                                + rule.getReviewDate() + ")",
                        "/requests/" + requestId);
            } else {
                notifier.notify(
                        members.activeUsernamesWithRole(MemberRole.ADMIN),
                        key + ":unassigned",
                        NotificationKind.REASSIGNMENT_REQUIRED,
                        "신청 #" + requestId + " · 방화벽 정기 재검토 담당자 재배정 필요",
                        "/requests/" + requestId);
            }
        }
    }

    private static String label(String status) {
        return switch (status) {
            case "OVERDUE" -> "정기 재검토 기한 지남";
            case "CHANGE_REQUIRED" -> "정기 재검토 후 변경 조치 필요";
            case "REVOKE_REQUIRED" -> "정기 재검토 후 회수 기록 필요";
            default -> "정기 재검토 필요";
        };
    }
}
