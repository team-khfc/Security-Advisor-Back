package kr.securityadvisor.llm.controller;

import jakarta.validation.Valid;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.dto.DocTypeSuggestionCommand;
import kr.securityadvisor.llm.dto.DocTypeSuggestionResult;
import kr.securityadvisor.llm.service.DocTypeSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** AI 서류 유형 제안(F-01). 저장하지 않는 계산이라 POST 로 "제안 생성"을 표현한다. 모델 실패는 503. */
@RestController
@RequiredArgsConstructor
public class DocTypeSuggestionController {

    private final DocTypeSuggestionService service;

    @PostMapping("/api/doc-type-suggestions")
    public DocTypeSuggestionResult suggest(
            @CurrentMember MemberPrincipal member, @Valid @RequestBody DocTypeSuggestionCommand command) {
        return service.suggest(member, command);
    }
}
