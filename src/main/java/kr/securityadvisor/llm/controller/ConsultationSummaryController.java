package kr.securityadvisor.llm.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import kr.securityadvisor.common.security.*;
import kr.securityadvisor.llm.service.ConsultationSummaryService;
import kr.securityadvisor.request.dto.ConsultationOverview.Summary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ConsultationSummaryController {
    private final ConsultationSummaryService service;

    public record Command(@NotBlank @Size(max = 64) String expectedFingerprint) {}

    @PostMapping("/api/security-requests/{id}/consultation-summaries")
    public Summary generate(
            @CurrentMember MemberPrincipal actor, @PathVariable Long id, @Valid @RequestBody Command command) {
        return service.generate(actor, id, command.expectedFingerprint());
    }
}
