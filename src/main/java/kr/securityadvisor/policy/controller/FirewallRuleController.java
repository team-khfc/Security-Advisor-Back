package kr.securityadvisor.policy.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.policy.dto.FirewallRuleResponse;
import kr.securityadvisor.policy.dto.RuleApplicationCommand;
import kr.securityadvisor.policy.dto.RuleRenewalCommand;
import kr.securityadvisor.policy.dto.RuleRenewalResult;
import kr.securityadvisor.policy.dto.RuleRevocationCommand;
import kr.securityadvisor.policy.service.FirewallRuleRenewalService;
import kr.securityadvisor.policy.service.FirewallRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 방화벽 규칙 API(F-15). 적용 기록·회수는 단일 하위 자원 설정(PUT …/application, …/revocation). */
@RestController
@RequiredArgsConstructor
public class FirewallRuleController {

    private final FirewallRuleService service;
    private final FirewallRuleRenewalService renewalService;

    @GetMapping("/api/security-requests/{id}/firewall-rules")
    public List<FirewallRuleResponse> listForRequest(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return service.listForRequest(member, id);
    }

    @GetMapping("/api/firewall-rules")
    public List<FirewallRuleResponse> list(
            @CurrentMember MemberPrincipal member, @RequestParam(required = false) String effectiveStatus) {
        return service.list(member, effectiveStatus);
    }

    /** 규칙 연장 신청(신청자). 규칙은 그대로 두고 값을 미리 채운 새 방화벽 신청을 만든다 → 평소 절차로 진행. */
    @PostMapping("/api/firewall-rules/{id}/renewal-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleRenewalResult requestRenewal(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody RuleRenewalCommand command) {
        return renewalService.requestRenewal(member, id, command);
    }

    /** 정기 재검토 이력 추가(동시 수정은 revision으로 차단). */
    @PostMapping("/api/firewall-rules/{id}/periodic-reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public FirewallRuleResponse review(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody kr.securityadvisor.policy.dto.PeriodicReviewCommand command) {
        return service.review(member, id, command);
    }

    @PutMapping("/api/firewall-rules/{id}/application")
    public FirewallRuleResponse recordApplication(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody RuleApplicationCommand command) {
        return service.recordApplication(member, id, command);
    }

    @PutMapping("/api/firewall-rules/{id}/revocation")
    public FirewallRuleResponse revoke(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody RuleRevocationCommand command) {
        return service.revoke(member, id, command);
    }
}
