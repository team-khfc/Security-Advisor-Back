package kr.securityadvisor.insight.controller;

import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.insight.dto.InsightSummary;
import kr.securityadvisor.insight.service.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 업무 지표 API(F-16). 읽기 전용, 기관 관리자만. */
@RestController
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    @GetMapping("/api/insights/summary")
    public InsightSummary summary(@CurrentMember MemberPrincipal member) {
        return insightService.summary(member);
    }
}
