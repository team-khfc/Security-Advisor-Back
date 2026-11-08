package kr.securityadvisor.policy.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.policy.dto.FirewallRuleResponse;
import kr.securityadvisor.policy.dto.RuleApplicationCommand;
import kr.securityadvisor.policy.dto.RuleRevocationCommand;
import kr.securityadvisor.policy.service.FirewallRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 방화벽 규칙 API(F-15). 적용 기록·회수는 단일 하위 자원 설정(PUT …/application, …/revocation). */
@RestController
@RequiredArgsConstructor
public class FirewallRuleController {

    private final FirewallRuleService service;

    @GetMapping("/api/security-requests/{id}/firewall-rules")
    public List<FirewallRuleResponse> listForRequest(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return service.listForRequest(member, id);
    }

    @GetMapping("/api/firewall-rules")
    public List<FirewallRuleResponse> list(
            @CurrentMember MemberPrincipal member, @RequestParam(required = false) String effectiveStatus) {
        return service.list(member, effectiveStatus);
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
