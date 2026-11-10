package kr.securityadvisor.request.controller;

import jakarta.validation.Valid;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.*;
import kr.securityadvisor.request.service.InfrastructurePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/security-requests/{id}/infrastructure-plan")
public class InfrastructurePlanController {
    private final InfrastructurePlanService service;

    @GetMapping
    public InfrastructurePlanResponse get(@CurrentMember MemberPrincipal actor, @PathVariable Long id) {
        return service.get(actor, id);
    }

    @PutMapping
    public InfrastructurePlanResponse save(
            @CurrentMember MemberPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody InfrastructurePlanCommand command) {
        return service.save(actor, id, command);
    }
}
