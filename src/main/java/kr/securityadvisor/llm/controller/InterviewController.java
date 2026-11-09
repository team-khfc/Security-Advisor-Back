package kr.securityadvisor.llm.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.dto.InterviewTurnCommand;
import kr.securityadvisor.llm.dto.InterviewTurnResponse;
import kr.securityadvisor.llm.service.InterviewService;
import kr.securityadvisor.request.dto.AnswerActionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 신청자 본인의 사업 건에 한정한 AI 인터뷰. 담당자 질의와 기관 비공개 컨텍스트를 공유하지 않는다. */
@RestController
@RequestMapping("/api/business-cases/{caseId}/interview-turns")
public class InterviewController {
    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    @GetMapping
    public List<InterviewTurnResponse> list(@CurrentMember MemberPrincipal member, @PathVariable Long caseId) {
        return service.list(member, caseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewTurnResponse ask(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long caseId,
            @Valid @RequestBody InterviewTurnCommand command) {
        return service.ask(member, caseId, command);
    }

    @PostMapping("/{turnId}/applications")
    public InterviewTurnResponse apply(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long caseId,
            @PathVariable Long turnId,
            @Valid @RequestBody AnswerActionRequest command) {
        return service.apply(member, caseId, turnId, command.expectedRevision());
    }
}
