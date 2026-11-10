package kr.securityadvisor.knowledge.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.knowledge.dto.FieldHelpResponse;
import kr.securityadvisor.knowledge.dto.KnowledgeSnippetCommand;
import kr.securityadvisor.knowledge.dto.KnowledgeSnippetResponse;
import kr.securityadvisor.knowledge.dto.PrivateContextCommand;
import kr.securityadvisor.knowledge.dto.PrivateContextResponse;
import kr.securityadvisor.knowledge.service.FactRecord;
import kr.securityadvisor.knowledge.service.FieldHelpService;
import kr.securityadvisor.knowledge.service.KnowledgeAdminService;
import kr.securityadvisor.knowledge.service.KnowledgeSearch;
import kr.securityadvisor.knowledge.service.PrivateContextLookup;
import kr.securityadvisor.knowledge.service.SnippetHit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 지식 기반 API(F-09): 공개 스니펫(조회는 로그인 사용자 누구나, 변경은 관리자), 비공개 컨텍스트(담당자 자동완성·관리자 관리). */
@RestController
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeAdminService adminService;
    private final KnowledgeSearch knowledgeSearch;
    private final FieldHelpService fieldHelpService;
    private final PrivateContextLookup privateContextLookup;

    @GetMapping("/api/knowledge-snippets")
    public List<KnowledgeSnippetResponse> listSnippets() {
        return adminService.listSnippets();
    }

    /** 자유 질의 검색(전문검색). */
    /** 항목 도움말 — 왜 묻는지(게시본 hint) + 공개자료 근거. 로그인한 누구나. docType·q(순위용 문장)·publicationId 는 선택. */
    @GetMapping("/api/field-help/{fieldKey}")
    public FieldHelpResponse fieldHelp(
            @PathVariable String fieldKey,
            @RequestParam(required = false) Long publicationId,
            @RequestParam(required = false) Long knowledgePublicationId,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String q) {
        DocTypeCode docTypeCode;
        try {
            docTypeCode = docType == null || docType.isBlank() ? null : DocTypeCode.of(docType);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("docType 값이 올바르지 않습니다: " + docType);
        }
        return fieldHelpService.help(fieldKey, publicationId, docTypeCode, q, knowledgePublicationId);
    }

    @GetMapping("/api/knowledge-snippets/search")
    public List<SnippetHit> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(required = false) Long knowledgePublicationId) {
        return knowledgePublicationId == null
                ? knowledgeSearch.search(q, Math.min(Math.max(limit, 1), 20))
                : knowledgeSearch.search(q, Math.min(Math.max(limit, 1), 20), knowledgePublicationId);
    }

    @PostMapping("/api/knowledge-snippets")
    public ResponseEntity<KnowledgeSnippetResponse> createSnippet(
            @CurrentMember MemberPrincipal member, @Valid @RequestBody KnowledgeSnippetCommand command) {
        KnowledgeSnippetResponse response = adminService.createSnippet(member, command);
        return ResponseEntity.created(URI.create("/api/knowledge-snippets/" + response.knowledgeSnippetId()))
                .body(response);
    }

    @PutMapping("/api/knowledge-snippets/{id}")
    public KnowledgeSnippetResponse updateSnippet(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeSnippetCommand command) {
        return adminService.updateSnippet(member, id, command);
    }

    /** 담당자 입력 자동완성 — 호출자 역할에 보이는 행만. 신청자는 항상 빈 목록. */
    @GetMapping("/api/private-contexts/suggestions")
    public List<FactRecord> suggest(
            @CurrentMember MemberPrincipal member,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long knowledgePublicationId) {
        return knowledgePublicationId == null
                ? privateContextLookup.suggest(member, q)
                : privateContextLookup.suggest(member, q, knowledgePublicationId);
    }

    @GetMapping("/api/private-contexts")
    public List<PrivateContextResponse> listPrivateContexts(@CurrentMember MemberPrincipal member) {
        return adminService.listPrivateContexts(member);
    }

    @PostMapping("/api/private-contexts")
    public ResponseEntity<PrivateContextResponse> createPrivateContext(
            @CurrentMember MemberPrincipal member, @Valid @RequestBody PrivateContextCommand command) {
        PrivateContextResponse response = adminService.createPrivateContext(member, command);
        return ResponseEntity.created(URI.create("/api/private-contexts/" + response.privateContextId()))
                .body(response);
    }

    @PutMapping("/api/private-contexts/{id}")
    public PrivateContextResponse updatePrivateContext(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody PrivateContextCommand command) {
        return adminService.updatePrivateContext(member, id, command);
    }
}
