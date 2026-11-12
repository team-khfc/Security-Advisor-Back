package kr.securityadvisor.llm.controller;

import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.service.ConsistencyCheckService;
import kr.securityadvisor.request.dto.ConsistencyCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 현재 답변과 첨부 문서의 읽기 전용 AI 비교 요청. */
@RestController
@RequiredArgsConstructor
public class ConsistencyCheckController {
    private final ConsistencyCheckService service;
    private final kr.securityadvisor.llm.service.ConsistencyReviewService records;

    public record Selection(java.util.List<Long> fileIds) {}

    /** 실제 업무 값은 수정하지 않고 확인할 후보만 반환한다. */
    @PostMapping("/api/security-requests/{id}/consistency-checks")
    public ConsistencyCheckResponse check(
            @CurrentMember MemberPrincipal actor,
            @PathVariable Long id,
            @RequestBody(required = false) Selection selection) {
        var result = service.check(actor, id, selection == null ? null : selection.fileIds());
        records.save(actor, id, result);
        return result;
    }

    @GetMapping("/api/security-requests/{id}/consistency-checks")
    public java.util.List<kr.securityadvisor.request.dto.ConsistencyReviewResponse> list(
            @CurrentMember MemberPrincipal actor, @PathVariable Long id, @RequestParam(defaultValue = "0") int page) {
        return records.list(actor, id, page);
    }

    @PostMapping("/api/security-requests/{id}/consistency-checks/{checkId}/decisions")
    public kr.securityadvisor.request.dto.ConsistencyReviewResponse decide(
            @CurrentMember MemberPrincipal actor,
            @PathVariable Long id,
            @PathVariable Long checkId,
            @jakarta.validation.Valid @RequestBody kr.securityadvisor.llm.dto.ConsistencyDecisionCommand command) {
        return records.decide(actor, id, checkId, command);
    }
}
