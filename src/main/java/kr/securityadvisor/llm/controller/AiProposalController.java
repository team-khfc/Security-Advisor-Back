package kr.securityadvisor.llm.controller;

import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.dto.AiProposalCommand;
import kr.securityadvisor.llm.dto.AiProposalResult;
import kr.securityadvisor.llm.service.AiProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** F-03 AI 제안 — 신청의 하위 자원 생성(POST …/ai-proposals). 제안은 미확정 값으로 저장되며 확정은 사용자 확인 API 가 한다. */
@RestController
@RequiredArgsConstructor
public class AiProposalController {

    private final AiProposalService aiProposalService;

    @PostMapping("/api/security-requests/{id}/ai-proposals")
    public AiProposalResult propose(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @RequestBody(required = false) AiProposalCommand command) {
        return aiProposalService.propose(member, id, command);
    }
}
