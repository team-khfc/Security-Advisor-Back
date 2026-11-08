package kr.securityadvisor.llm.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.dto.LlmConfigurationCommand;
import kr.securityadvisor.llm.dto.LlmConfigurationResponse;
import kr.securityadvisor.llm.service.LlmConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** LLM 구성 API(F-10, 관리자). 키 값은 요청·응답 어디에도 없다. */
@RestController
@RequestMapping("/api/llm-configurations")
@RequiredArgsConstructor
public class LlmConfigurationController {

    private final LlmConfigurationService service;

    @GetMapping
    public List<LlmConfigurationResponse> list(@CurrentMember MemberPrincipal member) {
        return service.list(member);
    }

    @PostMapping
    public ResponseEntity<LlmConfigurationResponse> create(
            @CurrentMember MemberPrincipal member, @Valid @RequestBody LlmConfigurationCommand command) {
        LlmConfigurationResponse response = service.create(member, command);
        return ResponseEntity.created(URI.create("/api/llm-configurations/" + response.llmConfigurationId()))
                .body(response);
    }

    @PutMapping("/{id}")
    public LlmConfigurationResponse update(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody LlmConfigurationCommand command) {
        return service.update(member, id, command);
    }
}
