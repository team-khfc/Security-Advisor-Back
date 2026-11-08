package kr.securityadvisor.insight.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditEvent;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.repository.AuditEventRepository;
import kr.securityadvisor.common.calendar.BusinessCalendar;
import kr.securityadvisor.common.schema.DeadlinePolicy;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.insight.dto.InsightSummary;
import kr.securityadvisor.policy.dto.FirewallRuleResponse;
import kr.securityadvisor.policy.service.FirewallRuleService;
import kr.securityadvisor.request.domain.Deadline;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.repository.DeadlineRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import kr.securityadvisor.review.domain.SupplementStatus;
import kr.securityadvisor.review.repository.SupplementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업무 지표(F-16): 감사 이력·업무 상태에서 조회 시 계산한다. 저장 상태 없음, 업무 상태 불변, 개인 순위 없음(불변식 29).
 * 1차 범위는 전체 집계 — 시연 규모에서 메모리 계산.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InsightService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final SecurityRequestRepository securityRequestRepository;
    private final DeadlineRepository deadlineRepository;
    private final SupplementRepository supplementRepository;
    private final AuditEventRepository auditEventRepository;
    private final FirewallRuleService firewallRuleService;
    private final PublicationService publicationService;
    private final Clock clock;

    public InsightSummary summary(MemberPrincipal principal) {
        principal.requireAnyRole(MemberRole.ADMIN, MemberRole.SECURITY, MemberRole.INFRA);
        List<SecurityRequest> requests = securityRequestRepository.findAll();

        Map<String, Long> byStage = new TreeMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (SecurityRequest r : requests) {
            byStatus.merge(r.getStatus().name(), 1L, Long::sum);
            if (!r.isTerminal()) {
                byStage.merge(String.valueOf(r.getStage()), 1L, Long::sum);
            }
        }

        DeadlinePolicy policy = publicationService
                .getFieldSchema(publicationService.currentPublicationId())
                .deadlinePolicy();
        BusinessCalendar calendar = new BusinessCalendar(policy.holidays());
        LocalDate today = LocalDate.ofInstant(clock.instant(), KOREA);
        Map<String, Long> deadlines = new LinkedHashMap<>(Map.of("ON_TRACK", 0L, "IMMINENT", 0L, "OVERDUE", 0L));
        for (Deadline d : deadlineRepository.findByActiveTrue()) {
            deadlines.merge(
                    d.statusOn(today, calendar, policy.imminentBusinessDays()).name(), 1L, Long::sum);
        }

        Map<String, Long> supplements = new LinkedHashMap<>();
        for (SupplementStatus s : SupplementStatus.values()) {
            supplements.put(s.name(), supplementRepository.countByStatus(s));
        }

        Map<String, Long> rules = new LinkedHashMap<>();
        for (FirewallRuleResponse r : firewallRuleService.list(principal, null)) {
            rules.merge(r.effectiveStatus(), 1L, Long::sum);
        }

        // 협의 소요: REVIEW_REQUESTED → STAGE_ADVANCED (같은 신청, 첫 쌍)
        Map<String, Instant> requested = new HashMap<>();
        long count = 0;
        double totalDays = 0;
        for (AuditEvent e : auditEventRepository.findByTargetTypeAndActionInOrderByOccurredAtAscIdAsc(
                AuditTargetType.SECURITY_REQUEST, List.of(AuditAction.REVIEW_REQUESTED, AuditAction.STAGE_ADVANCED))) {
            if (e.getAction() == AuditAction.REVIEW_REQUESTED) {
                requested.putIfAbsent(e.getTargetId(), e.getOccurredAt());
            } else if (requested.containsKey(e.getTargetId())) {
                totalDays += Duration.between(requested.remove(e.getTargetId()), e.getOccurredAt())
                                .toMinutes()
                        / 1440.0;
                count++;
            }
        }
        return new InsightSummary(
                byStage,
                byStatus,
                deadlines,
                supplements,
                rules,
                count == 0 ? null : Math.round(totalDays / count * 10) / 10.0,
                count);
    }
}
