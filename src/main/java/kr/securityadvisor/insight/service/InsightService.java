package kr.securityadvisor.insight.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import kr.securityadvisor.request.domain.DeadlineStatus;
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
 * 1차 범위는 운영 기관 전체 집계 — 시연 규모에서 메모리 계산.
 *
 * <p>단계 전이 시각은 감사 이력(SECURITY_REQUEST_CREATED → REVIEW_REQUESTED → STAGE_ADVANCED → SUBMITTED → REQUEST_CONFIRMED)에서
 * 신청별 첫 발생 시각을 쓴다. 차수 반환으로 같은 단계를 다시 거친 경우도 첫 통과 기준이다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InsightService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final List<AuditAction> STAGE_ACTIONS = List.of(
            AuditAction.SECURITY_REQUEST_CREATED,
            AuditAction.REVIEW_REQUESTED,
            AuditAction.STAGE_ADVANCED,
            AuditAction.SUBMITTED,
            AuditAction.REQUEST_CONFIRMED,
            AuditAction.SUPPLEMENT_REQUESTED,
            AuditAction.STAGE_RETURNED);
    private static final List<AuditAction> AI_ACTIONS = List.of(
            AuditAction.ANSWER_AI_PROPOSED,
            AuditAction.ANSWER_CONFIRMED,
            AuditAction.ANSWER_STAFF_CONFIRMED,
            AuditAction.ANSWER_UPDATED,
            AuditAction.ANSWER_STAFF_INPUT);

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

        Map<String, Long> byDocType = new TreeMap<>();
        Map<String, Long> byStage = new TreeMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (SecurityRequest r : requests) {
            byDocType.merge(r.getDocType().code(), 1L, Long::sum);
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
        long overdue = deadlines.getOrDefault(DeadlineStatus.OVERDUE.name(), 0L);

        Map<String, Long> supplements = new LinkedHashMap<>();
        long supplementTotal = 0;
        for (SupplementStatus s : SupplementStatus.values()) {
            long n = supplementRepository.countByStatus(s);
            supplements.put(s.name(), n);
            supplementTotal += n;
        }

        Map<String, Long> rules = new LinkedHashMap<>();
        for (FirewallRuleResponse r : firewallRuleService.list(principal, null)) {
            rules.merge(r.effectiveStatus(), 1L, Long::sum);
        }

        StageTimeline timeline = stageTimeline(calendar);
        long reviewed = timeline.firstAt(AuditAction.REVIEW_REQUESTED).size();
        long confirmed = timeline.firstAt(AuditAction.REQUEST_CONFIRMED).size();
        long firstPass = timeline.firstAt(AuditAction.REQUEST_CONFIRMED).keySet().stream()
                .filter(id -> !timeline.supplemented.contains(id) && !timeline.returned.contains(id))
                .count();
        AiProposalStats ai = aiProposalStats();

        return new InsightSummary(
                byDocType,
                byStage,
                byStatus,
                timeline.averageBusinessDays,
                timeline.completedCount,
                deadlines,
                overdue,
                supplements,
                ratio(supplementTotal, reviewed, 2),
                ratio(
                        timeline.returned.stream()
                                .filter(timeline.firstAt(AuditAction.REVIEW_REQUESTED)::containsKey)
                                .count(),
                        reviewed,
                        3),
                ratio(firstPass, confirmed, 3),
                ai.proposed,
                ai.confirmedUnchanged,
                ai.modified,
                ratio(ai.confirmedUnchanged, ai.confirmedUnchanged + ai.modified, 3),
                rules,
                timeline.averageReviewCalendarDays,
                timeline.completedCount.getOrDefault("2", 0L));
    }

    /** 신청별 단계 전이 시각(첫 발생)과 단계별 평균 소요 영업일. */
    private StageTimeline stageTimeline(BusinessCalendar calendar) {
        StageTimeline t = new StageTimeline();
        for (AuditEvent e : auditEventRepository.findByTargetTypeAndActionInOrderByOccurredAtAscIdAsc(
                AuditTargetType.SECURITY_REQUEST, STAGE_ACTIONS)) {
            switch (e.getAction()) {
                case SUPPLEMENT_REQUESTED -> t.supplemented.add(e.getTargetId());
                case STAGE_RETURNED -> t.returned.add(e.getTargetId());
                default -> t.firstAt(e.getAction()).putIfAbsent(e.getTargetId(), e.getOccurredAt());
            }
        }
        record Leg(String stage, AuditAction from, AuditAction to) {}
        List<Leg> legs = List.of(
                new Leg("1", AuditAction.SECURITY_REQUEST_CREATED, AuditAction.REVIEW_REQUESTED),
                new Leg("2", AuditAction.REVIEW_REQUESTED, AuditAction.STAGE_ADVANCED),
                new Leg("3", AuditAction.STAGE_ADVANCED, AuditAction.SUBMITTED),
                new Leg("4", AuditAction.SUBMITTED, AuditAction.REQUEST_CONFIRMED));
        for (Leg leg : legs) {
            long count = 0;
            long businessDays = 0;
            double calendarDays = 0;
            for (Map.Entry<String, Instant> end : t.firstAt(leg.to()).entrySet()) {
                Instant start = t.firstAt(leg.from()).get(end.getKey());
                if (start == null || end.getValue().isBefore(start)) {
                    continue;
                }
                count++;
                businessDays += calendar.businessDaysBetween(
                        LocalDate.ofInstant(start, KOREA), LocalDate.ofInstant(end.getValue(), KOREA));
                calendarDays += Duration.between(start, end.getValue()).toMinutes() / 1440.0;
            }
            t.completedCount.put(leg.stage(), count);
            t.averageBusinessDays.put(leg.stage(), count == 0 ? null : round(businessDays / (double) count, 1));
            if (leg.stage().equals("2")) {
                t.averageReviewCalendarDays = count == 0 ? null : round(calendarDays / count, 1);
            }
        }
        return t;
    }

    /**
     * AI 제안 확인율: 감사 이력에서 항목(대상·항목 키)마다 AI 제안 뒤 첫 결론을 본다 — 사용자·담당자 확인이면 "수정 없이 확인",
     * 그 전에 사용자 답변·담당자 입력으로 값이 바뀌었으면 "수정". 아직 결론이 없으면 계산에서 뺀다.
     */
    private AiProposalStats aiProposalStats() {
        AiProposalStats stats = new AiProposalStats();
        Set<String> open = new HashSet<>();
        for (AuditEvent e : auditEventRepository.findByActionInOrderByOccurredAtAscIdAsc(AI_ACTIONS)) {
            String key = e.getTargetType() + ":" + e.getTargetId() + ":" + fieldKeyOf(e.getSummary());
            switch (e.getAction()) {
                case ANSWER_AI_PROPOSED -> {
                    stats.proposed++;
                    open.add(key);
                }
                case ANSWER_CONFIRMED, ANSWER_STAFF_CONFIRMED -> {
                    if (open.remove(key)) {
                        stats.confirmedUnchanged++;
                    }
                }
                case ANSWER_UPDATED, ANSWER_STAFF_INPUT -> {
                    if (open.remove(key)) {
                        stats.modified++;
                    }
                }
                default -> {}
            }
        }
        return stats;
    }

    /** 답변 감사 요약은 "{fieldKey} …" 로 시작한다(AnswerService.audit). */
    private static String fieldKeyOf(String summary) {
        if (summary == null || summary.isBlank()) {
            return "";
        }
        int space = summary.indexOf(' ');
        return space < 0 ? summary : summary.substring(0, space);
    }

    private static Double ratio(long numerator, long denominator, int scale) {
        return denominator == 0 ? null : round(numerator / (double) denominator, scale);
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private static final class StageTimeline {
        final Map<AuditAction, Map<String, Instant>> firstAt = new HashMap<>();
        final Set<String> supplemented = new HashSet<>();
        final Set<String> returned = new HashSet<>();
        final Map<String, Double> averageBusinessDays = new TreeMap<>();
        final Map<String, Long> completedCount = new TreeMap<>();
        Double averageReviewCalendarDays;

        Map<String, Instant> firstAt(AuditAction action) {
            return firstAt.computeIfAbsent(action, a -> new HashMap<>());
        }
    }

    private static final class AiProposalStats {
        long proposed;
        long confirmedUnchanged;
        long modified;
    }
}
