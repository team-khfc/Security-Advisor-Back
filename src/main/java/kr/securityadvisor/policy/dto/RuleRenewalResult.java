package kr.securityadvisor.policy.dto;

import java.util.List;
import kr.securityadvisor.request.dto.SecurityRequestSummary;

/**
 * 연장 신청 결과.
 *
 * @param rule 원 규칙(renewalRequestId 가 채워짐, 값은 그대로)
 * @param request 새로 만든 연장 신청(1단계)
 * @param prefilledFieldKeys 미리 채운 서류별 항목 키
 */
public record RuleRenewalResult(
        FirewallRuleResponse rule, SecurityRequestSummary request, List<String> prefilledFieldKeys) {}
