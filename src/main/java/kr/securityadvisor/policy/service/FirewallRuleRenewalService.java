package kr.securityadvisor.policy.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.policy.domain.FirewallRule;
import kr.securityadvisor.policy.domain.RulePeriod;
import kr.securityadvisor.policy.dto.FirewallRuleResponse;
import kr.securityadvisor.policy.dto.RuleRenewalCommand;
import kr.securityadvisor.policy.dto.RuleRenewalResult;
import kr.securityadvisor.policy.repository.FirewallRuleRepository;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.AddSecurityRequestCommand;
import kr.securityadvisor.request.dto.SecurityRequestSummary;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import kr.securityadvisor.request.service.AnswerService;
import kr.securityadvisor.request.service.BusinessCaseService;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방화벽 규칙 연장(F-15 P3). 기존 규칙은 고치지 않는다 — 규칙을 참조해 값을 미리 채운 **새 신청**을 같은 사업 건에 만들고,
 * 그 신청이 평소 절차(협의·제출·확정)를 거쳐 확정되면 새 규칙이 생긴다. 옛 규칙의 회수는 INFRA 담당자가 사람으로서 기록한다(불변식 27).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FirewallRuleRenewalService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final FirewallRuleRepository repository;
    private final SecurityRequestRepository securityRequestRepository;
    private final RequestAccessPolicy accessPolicy;
    private final BusinessCaseService businessCaseService;
    private final AnswerService answerService;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    /**
     * @throws DomainRuleViolationException 적용 완료 규칙이 아니거나 진행 중 연장이 있거나 기간 형식 오류(422)
     * @throws NotFoundException 규칙이 없거나 신청자가 남의 규칙일 때(404)
     */
    public RuleRenewalResult requestRenewal(MemberPrincipal principal, Long ruleId, RuleRenewalCommand command) {
        FirewallRule rule =
                repository.findById(ruleId).orElseThrow(() -> new NotFoundException("방화벽 규칙을 찾을 수 없습니다: " + ruleId));
        SecurityRequest original = accessPolicy.ownedRequest(principal, rule.getSecurityRequestId());
        RulePeriod.parse(command.newPeriod()); // 형식 검증(422)
        boolean previousOpen = rule.getRenewalRequestId() != null
                && securityRequestRepository
                        .findById(rule.getRenewalRequestId())
                        .map(r -> !r.isTerminal())
                        .orElse(false);
        rule.requireRenewable(previousOpen);

        SecurityRequestSummary created = businessCaseService.addRequest(
                principal, original.getBusinessCaseId(), new AddSecurityRequestCommand(original.getDocType(), ruleId));
        SecurityRequest renewal =
                securityRequestRepository.findById(created.securityRequestId()).orElseThrow();
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("firewall.period", command.newPeriod());
        overrides.put("firewall.protocol", rule.getProtocol());
        overrides.put("firewall.port", rule.getPort());
        if (command.note() != null && !command.note().isBlank()) {
            overrides.put(
                    "firewall.purpose",
                    "[기존 규칙 " + ruleId + " 연장] " + command.note().strip());
        }
        List<String> filled = answerService.prefillRequestAnswers(principal, original, renewal, overrides);
        rule.linkRenewal(renewal.getId());
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(original.getId()),
                null,
                AuditAction.FIREWALL_RULE_RENEWAL_REQUESTED,
                command.note(),
                "규칙 " + ruleId + " 연장 신청 생성 → 신청 " + renewal.getId() + " (새 기간 " + command.newPeriod() + ", 미리 채움 "
                        + filled + ")");
        return new RuleRenewalResult(
                FirewallRuleResponse.from(
                        rule, LocalDate.ofInstant(clock.instant(), KOREA), FirewallRuleService.EXPIRY_NOTICE_DAYS),
                created,
                filled);
    }
}
