package kr.securityadvisor.policy.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.DocTypeDefinition;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.policy.domain.FirewallRule;
import kr.securityadvisor.policy.domain.RulePeriod;
import kr.securityadvisor.policy.dto.FirewallRuleResponse;
import kr.securityadvisor.policy.dto.RuleApplicationCommand;
import kr.securityadvisor.policy.dto.RuleRevocationCommand;
import kr.securityadvisor.policy.repository.FirewallRuleRepository;
import kr.securityadvisor.request.domain.Submission;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.request.service.SubmissionService;
import kr.securityadvisor.review.service.RequestConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 방화벽 규칙 수명주기(F-15). 확정 이벤트를 받아 제출본 스냅샷에서 규칙을 만들고(포트가 쉼표로 여럿이면 규칙도 여럿),
 * 적용 완료·적용 불가·회수는 INFRA 담당자의 기록으로만 전이한다. 만료 예정·만료는 조회 시 계산.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FirewallRuleService {

    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    static final int EXPIRY_NOTICE_DAYS = 30;

    private final FirewallRuleRepository repository;
    private final SubmissionService submissionService;
    private final RequestAccessPolicy accessPolicy;
    private final PublicationService publicationService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** 확정 시 규칙 생성(불변식 26). 규칙을 만들지 않는 서류 유형이면 아무것도 하지 않는다. 같은 제출본에 두 번 만들지 않는다. */
    @EventListener
    public void onConfirmed(RequestConfirmedEvent event) {
        Submission submission = submissionService.requireSubmission(event.submissionId());
        boolean generates = publicationService
                .getFieldSchema(submission.getPublicationId())
                .findDocType(event.docType())
                .map(DocTypeDefinition::generatesFirewallRules)
                .orElse(false);
        if (!generates || repository.existsBySubmissionId(submission.getId())) {
            return;
        }
        Map<String, String> values = snapshotValues(submission.getSnapshot());
        RulePeriod period = RulePeriod.parse(values.get("firewall.period"));
        List<String> ports = new ArrayList<>();
        for (String p : String.valueOf(values.getOrDefault("firewall.port", "")).split(",")) {
            if (!p.isBlank()) {
                ports.add(p.strip());
            }
        }
        if (ports.isEmpty()) {
            throw new DomainRuleViolationException("확정 제출본에 포트 값이 없어 규칙을 만들 수 없습니다");
        }
        int no = 1;
        List<Long> created = new ArrayList<>();
        for (String port : ports) {
            FirewallRule rule = repository.save(FirewallRule.fromConfirmedSubmission(
                    event.securityRequestId(),
                    submission.getId(),
                    no++,
                    values.get("network.source"),
                    values.get("network.destination"),
                    values.get("firewall.protocol"),
                    port,
                    period.start(),
                    period.end(),
                    period.reviewDate()));
            created.add(rule.getId());
        }
        auditRecorder.record(
                event.actor(),
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(event.securityRequestId()),
                null,
                AuditAction.FIREWALL_RULES_CREATED,
                null,
                "확정 제출본 " + submission.getId() + " 에서 방화벽 규칙 " + created.size() + "건 생성 " + created + " (적용 대기)");
    }

    @Transactional(readOnly = true)
    public List<FirewallRuleResponse> listForRequest(MemberPrincipal principal, Long securityRequestId) {
        accessPolicy.viewableRequest(principal, securityRequestId);
        return repository.findBySecurityRequestIdOrderByRuleNoAsc(securityRequestId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 규칙 목록: 신청자는 자기 사업 건, 담당자·관리자는 전체. effectiveStatus 로 필터(선택). */
    @Transactional(readOnly = true)
    public List<FirewallRuleResponse> list(MemberPrincipal principal, String effectiveStatus) {
        List<FirewallRule> rules = principal.hasRole(MemberRole.APPLICANT)
                ? repository.findAllForApplicant(principal.username())
                : repository.findAllByOrderByIdDesc();
        return rules.stream()
                .map(this::toResponse)
                .filter(r -> effectiveStatus == null
                        || effectiveStatus.isBlank()
                        || r.effectiveStatus().equals(effectiveStatus))
                .toList();
    }

    /** 적용 완료 / 적용 불가 기록 — INFRA 역할만(불변식 27). */
    public FirewallRuleResponse recordApplication(
            MemberPrincipal principal, Long ruleId, RuleApplicationCommand command) {
        principal.requireAnyRole(MemberRole.INFRA);
        FirewallRule rule = requireRule(ruleId);
        rule.requireRevision(command.expectedRevision());
        Instant now = clock.instant();
        AuditAction action;
        if (command.result() == RuleApplicationCommand.Result.APPLIED) {
            rule.recordApplied(principal.username(), command.note(), now);
            action = AuditAction.FIREWALL_RULE_APPLIED;
        } else {
            rule.recordNotApplicable(principal.username(), command.reason(), now);
            action = AuditAction.FIREWALL_RULE_NOT_APPLICABLE;
        }
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(rule.getSecurityRequestId()),
                null,
                action,
                command.reason(),
                "규칙 " + ruleId + " " + rule.getStatus().label() + " 기록 (사람 기록, 자동 적용 아님)");
        return toResponse(rule);
    }

    /** 회수 — INFRA 역할만, 사유 필수. */
    public FirewallRuleResponse revoke(MemberPrincipal principal, Long ruleId, RuleRevocationCommand command) {
        principal.requireAnyRole(MemberRole.INFRA);
        FirewallRule rule = requireRule(ruleId);
        rule.requireRevision(command.expectedRevision());
        rule.revoke(principal.username(), command.reason(), clock.instant());
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(rule.getSecurityRequestId()),
                null,
                AuditAction.FIREWALL_RULE_REVOKED,
                command.reason(),
                "규칙 " + ruleId + " 회수");
        return toResponse(rule);
    }

    private FirewallRule requireRule(Long ruleId) {
        return repository.findById(ruleId).orElseThrow(() -> new NotFoundException("방화벽 규칙을 찾을 수 없습니다: " + ruleId));
    }

    private FirewallRuleResponse toResponse(FirewallRule rule) {
        return FirewallRuleResponse.from(rule, LocalDate.ofInstant(clock.instant(), KOREA), EXPIRY_NOTICE_DAYS);
    }

    private Map<String, String> snapshotValues(String snapshotJson) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        JsonNode root = objectMapper.readTree(snapshotJson);
        for (JsonNode a : root.path("answers")) {
            if (!a.path("value").isNull()) {
                values.put(a.path("fieldKey").asString(""), a.path("value").asString(""));
            }
        }
        return values;
    }
}
