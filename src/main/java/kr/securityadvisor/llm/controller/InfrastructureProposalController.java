package kr.securityadvisor.llm.controller;

import jakarta.validation.Valid;
import kr.securityadvisor.common.security.*;
import kr.securityadvisor.llm.dto.*;
import kr.securityadvisor.llm.service.InfrastructureProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 망 구성 AI 제안은 확인·저장 API와 분리한다. */
@RestController
@RequiredArgsConstructor
public class InfrastructureProposalController {
    private final InfrastructureProposalService service;

    @PostMapping("/api/security-requests/{id}/infrastructure-proposals")
    public InfrastructureProposalResponse generate(
            @CurrentMember MemberPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody InfrastructureProposalCommand command) {
        return service.generate(actor, id, command);
    }
}
