package kr.securityadvisor.llm.controller;

import jakarta.validation.Valid;
import java.net.URI;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.dto.LlmConnectionTestCommand;
import kr.securityadvisor.llm.dto.LlmConnectionTestResponse;
import kr.securityadvisor.llm.service.LlmConnectionTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 연결 시험 자원을 만들며 실패 결과도 status=FAILED로 명확히 반환한다. */
@RestController
@RequestMapping("/api/llm-connection-tests")
@RequiredArgsConstructor
public class LlmConnectionTestController {
    private final LlmConnectionTestService service;

    /** 고정 합성 입력만 시험하고 설정을 변경하지 않는다. */
    @PostMapping
    public ResponseEntity<LlmConnectionTestResponse> test(
            @CurrentMember MemberPrincipal member, @Valid @RequestBody LlmConnectionTestCommand command) {
        var result = service.test(member, command);
        return ResponseEntity.created(URI.create("/api/llm-connection-tests/" + result.testId()))
                .body(result);
    }

    /** 현재 관리자 권한을 재확인한 뒤 저장된 자원을 조회한다. */
    @GetMapping("/{id}")
    public LlmConnectionTestResponse get(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return service.get(member, id);
    }
}
