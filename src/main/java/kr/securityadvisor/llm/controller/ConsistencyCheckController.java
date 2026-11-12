package kr.securityadvisor.llm.controller;

import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.dto.ConsistencyCheckResponse;
import kr.securityadvisor.llm.service.ConsistencyCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 현재 답변과 첨부 문서의 읽기 전용 AI 비교 요청. */
@RestController
@RequiredArgsConstructor
public class ConsistencyCheckController {
    private final ConsistencyCheckService service;

    /** 실제 업무 값은 수정하지 않고 확인할 후보만 반환한다. */
    @PostMapping("/api/security-requests/{id}/consistency-checks")
    public ConsistencyCheckResponse check(@CurrentMember MemberPrincipal actor, @PathVariable Long id) {
        return service.check(actor, id);
    }
}
