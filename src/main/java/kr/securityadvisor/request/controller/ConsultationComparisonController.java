package kr.securityadvisor.request.controller;

import kr.securityadvisor.common.security.*;
import kr.securityadvisor.request.dto.ConsultationOverview;
import kr.securityadvisor.request.service.ConsultationComparisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ConsultationComparisonController {
    private final ConsultationComparisonService service;

    @GetMapping("/api/security-requests/{id}/consultation-comparison")
    public ConsultationOverview get(@CurrentMember MemberPrincipal actor, @PathVariable Long id) {
        return service.get(actor, id);
    }
}
