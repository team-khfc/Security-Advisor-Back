package kr.securityadvisor.knowledge.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.knowledge.dto.KnowledgeSnippetCommand;
import kr.securityadvisor.knowledge.dto.KnowledgeSnippetResponse;
import kr.securityadvisor.knowledge.dto.PrivateContextCommand;
import kr.securityadvisor.knowledge.dto.PrivateContextResponse;
import kr.securityadvisor.knowledge.service.FactRecord;
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
    private final PrivateContextLookup privateContextLookup;

    @GetMapping("/api/knowledge-snippets")
    public List<KnowledgeSnippetResponse> listSnippets() {
        return adminService.listSnippets();
    }

    /** 자유 질의 검색(전문검색). */
    @GetMapping("/api/knowledge-snippets/search")
    public List<SnippetHit> search(@RequestParam String q, @RequestParam(defaultValue = "5") int limit) {
        return knowledgeSearch.search(q, Math.min(Math.max(limit, 1), 20));
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
    public List<FactRecord> suggest(@CurrentMember MemberPrincipal member, @RequestParam(required = false) String q) {
        return privateContextLookup.suggest(member, q);
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
