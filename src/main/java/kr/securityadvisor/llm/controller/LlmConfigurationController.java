package kr.securityadvisor.llm.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.dto.LlmComparisonResponse;
import kr.securityadvisor.llm.dto.LlmConfigurationCommand;
import kr.securityadvisor.llm.dto.LlmConfigurationResponse;
import kr.securityadvisor.llm.dto.LlmHistoryResponse;
import kr.securityadvisor.llm.dto.LlmManagementResponse;
import kr.securityadvisor.llm.dto.LlmRestoreCommand;
import kr.securityadvisor.llm.service.LlmConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** LLM 구성 API(F-10, 관리자). 키 값은 요청·응답 어디에도 없다. */
@RestController
@RequestMapping("/api/llm-configurations")
@RequiredArgsConstructor
public class LlmConfigurationController {

    private final LlmConfigurationService service;

    /** 시연 응답과 실제 용도별 선택 결과를 구분한다. */
    @GetMapping("/management")
    public LlmManagementResponse management(@CurrentMember MemberPrincipal member) {
        return service.management(member);
    }

    /** 현재 관리자 권한을 재확인한 뒤 저장된 자원을 조회한다. */
    @GetMapping("/{id}")
    public LlmConfigurationResponse get(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return service.get(member, id);
    }

    /** 새 구성도 저장과 동일한 규칙으로 미리 검증한다. */
    @PostMapping("/comparisons")
    public LlmComparisonResponse compareNew(
            @CurrentMember MemberPrincipal member, @Valid @RequestBody LlmConfigurationCommand command) {
        return service.compare(member, null, command);
    }

    /** 저장하지 않고 같은 검증 규칙으로 변경 전후를 비교한다. */
    @PostMapping("/{id}/comparisons")
    public LlmComparisonResponse compare(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody LlmConfigurationCommand command) {
        return service.compare(member, id, command);
    }

    /** 구성별 불변 이력을 페이지 단위로 제공한다. */
    @GetMapping("/{id}/history")
    public PageResponse<LlmHistoryResponse> history(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(member, id, page, size);
    }

    /** 과거 값을 현재 정책으로 검증해 새 이력으로 복원한다. */
    @PostMapping("/{id}/restorations")
    public LlmConfigurationResponse restore(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody LlmRestoreCommand command) {
        return service.restore(member, id, command);
    }

    /** 관리자에게만 키 값 없이 구성을 제공한다. */
    @GetMapping
    public List<LlmConfigurationResponse> list(@CurrentMember MemberPrincipal member) {
        return service.list(member);
    }

    /** 명시한 활성 상태를 보존하고 최초 이력을 함께 기록한다. */
    @PostMapping
    public ResponseEntity<LlmConfigurationResponse> create(
            @CurrentMember MemberPrincipal member, @Valid @RequestBody LlmConfigurationCommand command) {
        LlmConfigurationResponse response = service.create(member, command);
        return ResponseEntity.created(URI.create("/api/llm-configurations/" + response.llmConfigurationId()))
                .body(response);
    }

    /** 기준 버전이 일치할 때만 구성과 이력을 함께 변경한다. */
    @PutMapping("/{id}")
    public LlmConfigurationResponse update(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody LlmConfigurationCommand command) {
        return service.update(member, id, command);
    }
}
