package kr.securityadvisor.knowledge.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.knowledge.dto.KnowledgeComparisonResponse;
import kr.securityadvisor.knowledge.dto.KnowledgeDraftCommand;
import kr.securityadvisor.knowledge.dto.KnowledgeDraftResponse;
import kr.securityadvisor.knowledge.dto.KnowledgePublicationResponse;
import kr.securityadvisor.knowledge.dto.KnowledgeSnapshot;
import kr.securityadvisor.knowledge.dto.KnowledgeSnippetResponse;
import kr.securityadvisor.knowledge.dto.PrivateContextResponse;
import kr.securityadvisor.knowledge.service.KnowledgePublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 초안·비공개 원문·게시 이력은 모두 관리자 전용이다. */
@RestController
@RequiredArgsConstructor
public class KnowledgePublicationController {
    private final KnowledgePublicationService service;

    @GetMapping("/api/knowledge-drafts/current/snippets/{id}")
    public KnowledgeSnippetResponse snippet(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return service.snippetDraft(member, id);
    }

    @GetMapping("/api/knowledge-drafts/current/private-contexts/{id}")
    public PrivateContextResponse context(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return service.contextDraft(member, id);
    }

    public record PublishCommand(@NotNull Long expectedRevision) {}

    @GetMapping("/api/knowledge-drafts/current")
    public KnowledgeDraftResponse current(@CurrentMember MemberPrincipal member) {
        return service.draft(member);
    }

    @PutMapping("/api/knowledge-drafts/current")
    public KnowledgeDraftResponse save(
            @CurrentMember MemberPrincipal member, @Valid @RequestBody KnowledgeDraftCommand command) {
        return service.saveNote(member, command);
    }

    @GetMapping("/api/knowledge-drafts/current/snippets")
    public PageResponse<KnowledgeSnippetResponse> snippets(
            @CurrentMember MemberPrincipal member,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.snippetDrafts(member, q, page, size);
    }

    @GetMapping("/api/knowledge-drafts/current/private-contexts")
    public PageResponse<PrivateContextResponse> contexts(
            @CurrentMember MemberPrincipal member,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.contextDrafts(member, q, page, size);
    }

    @GetMapping("/api/knowledge-drafts/current/comparison")
    public KnowledgeComparisonResponse compare(@CurrentMember MemberPrincipal member) {
        return service.compare(member);
    }

    @PostMapping("/api/knowledge-drafts/current/publications")
    public ResponseEntity<KnowledgePublicationResponse> publish(
            @CurrentMember MemberPrincipal member, @Valid @RequestBody PublishCommand command) {
        var result = service.publish(member, command.expectedRevision());
        return ResponseEntity.created(URI.create("/api/knowledge-publications/" + result.knowledgePublicationId()))
                .body(result);
    }

    @GetMapping("/api/knowledge-publications")
    public PageResponse<KnowledgePublicationResponse> history(
            @CurrentMember MemberPrincipal member,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(member, page, size);
    }

    @GetMapping("/api/knowledge-publications/{id}")
    public KnowledgeSnapshot content(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return service.publishedContent(member, id);
    }

    @GetMapping("/api/knowledge-publications/{id}/comparison")
    public KnowledgeComparisonResponse publishedComparison(
            @CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return service.publishedComparison(member, id);
    }
}
