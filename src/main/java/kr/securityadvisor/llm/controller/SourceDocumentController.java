package kr.securityadvisor.llm.controller;

import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.dto.SourceDocumentResponse;
import kr.securityadvisor.llm.service.SourceDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 권한 범위 내 첨부 원문의 읽기 전용 추출문. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/business-cases/{id}/source-documents")
public class SourceDocumentController {
    private final SourceDocumentService service;

    @GetMapping("/{fileId}")
    public SourceDocumentResponse get(
            @CurrentMember MemberPrincipal member, @PathVariable Long id, @PathVariable Long fileId) {
        return service.get(member, id, fileId);
    }
}
