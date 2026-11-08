package kr.securityadvisor.knowledge.service;

import java.util.List;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.json.JsonCodec;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.knowledge.domain.KnowledgeSnippet;
import kr.securityadvisor.knowledge.domain.PrivateContext;
import kr.securityadvisor.knowledge.dto.KnowledgeSnippetCommand;
import kr.securityadvisor.knowledge.dto.KnowledgeSnippetResponse;
import kr.securityadvisor.knowledge.dto.PrivateContextCommand;
import kr.securityadvisor.knowledge.dto.PrivateContextResponse;
import kr.securityadvisor.knowledge.repository.KnowledgeSnippetRepository;
import kr.securityadvisor.knowledge.repository.PrivateContextRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지식·비공개 컨텍스트 관리(F-09 관리 기능, 관리자). 변경은 감사 이력에 남긴다(값은 요약만). */
@Service
@Transactional
@RequiredArgsConstructor
public class KnowledgeAdminService {

    private final KnowledgeSnippetRepository snippetRepository;
    private final PrivateContextRepository privateContextRepository;
    private final JsonCodec json;
    private final AuditRecorder auditRecorder;

    @Transactional(readOnly = true)
    public List<KnowledgeSnippetResponse> listSnippets() {
        return snippetRepository.findAllByOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public KnowledgeSnippetResponse createSnippet(MemberPrincipal principal, KnowledgeSnippetCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        List<String> fieldKeys = distinct(command.fieldKeys());
        KnowledgeSnippet snippet = snippetRepository.save(KnowledgeSnippet.create(
                command.sourceTitle(),
                command.sourceVersion(),
                command.sourceLocator(),
                json.writeStrings(fieldKeys),
                !fieldKeys.isEmpty(),
                json.writeStrings(distinct(command.docTypes())),
                command.body()));
        audit(
                principal,
                AuditTargetType.KNOWLEDGE_SNIPPET,
                snippet.getId(),
                snippet.getRevision(),
                AuditAction.KNOWLEDGE_SNIPPET_CREATED,
                "스니펫 생성 " + snippet.citation() + " " + fieldKeys);
        return toResponse(snippet);
    }

    public KnowledgeSnippetResponse updateSnippet(MemberPrincipal principal, Long id, KnowledgeSnippetCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        KnowledgeSnippet snippet =
                snippetRepository.findById(id).orElseThrow(() -> new NotFoundException("스니펫을 찾을 수 없습니다: " + id));
        if (command.expectedRevision() == null) {
            throw new DomainRuleViolationException("수정에는 expectedRevision 이 필요합니다");
        }
        snippet.requireRevision(command.expectedRevision());
        List<String> fieldKeys = distinct(command.fieldKeys());
        snippet.update(
                command.sourceTitle(),
                command.sourceVersion(),
                command.sourceLocator(),
                json.writeStrings(fieldKeys),
                !fieldKeys.isEmpty(),
                json.writeStrings(distinct(command.docTypes())),
                command.body(),
                command.active() == null || command.active());
        audit(
                principal,
                AuditTargetType.KNOWLEDGE_SNIPPET,
                id,
                snippet.getRevision(),
                AuditAction.KNOWLEDGE_SNIPPET_UPDATED,
                "스니펫 수정 " + snippet.citation() + " " + fieldKeys);
        return toResponse(snippet);
    }

    @Transactional(readOnly = true)
    public List<PrivateContextResponse> listPrivateContexts(MemberPrincipal principal) {
        principal.requireAnyRole(MemberRole.ADMIN);
        List<PrivateContextResponse> contexts = privateContextRepository.findAllByOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
        // 비공개 컨텍스트 조회는 값 없이 건수만 감사 이력에 남긴다(F-14, 불변식 25)
        auditRecorder.recordAccess(
                principal,
                AuditTargetType.PRIVATE_CONTEXT,
                "*",
                AuditAction.PRIVATE_CONTEXT_VIEWED,
                "비공개 컨텍스트 목록 조회 " + contexts.size() + "건");
        return contexts;
    }

    public PrivateContextResponse createPrivateContext(MemberPrincipal principal, PrivateContextCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        List<String> roles = distinct(command.visibleRoles());
        PrivateContext context = privateContextRepository.save(PrivateContext.create(
                command.kind(),
                command.name(),
                json.writeStrings(distinct(command.aliases())),
                json.writeStringMap(command.attributes()),
                json.writeStrings(roles),
                roles.contains(MemberRole.APPLICANT.name()),
                command.note()));
        audit(
                principal,
                AuditTargetType.PRIVATE_CONTEXT,
                context.getId(),
                context.getRevision(),
                AuditAction.PRIVATE_CONTEXT_CREATED,
                "비공개 컨텍스트 생성 " + context.getKind() + " (값은 기록하지 않음)");
        return toResponse(context);
    }

    public PrivateContextResponse updatePrivateContext(
            MemberPrincipal principal, Long id, PrivateContextCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        PrivateContext context = privateContextRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("비공개 컨텍스트를 찾을 수 없습니다: " + id));
        if (command.expectedRevision() == null) {
            throw new DomainRuleViolationException("수정에는 expectedRevision 이 필요합니다");
        }
        context.requireRevision(command.expectedRevision());
        List<String> roles = distinct(command.visibleRoles());
        context.update(
                command.kind(),
                command.name(),
                json.writeStrings(distinct(command.aliases())),
                json.writeStringMap(command.attributes()),
                json.writeStrings(roles),
                roles.contains(MemberRole.APPLICANT.name()),
                command.note(),
                command.active() == null || command.active());
        audit(
                principal,
                AuditTargetType.PRIVATE_CONTEXT,
                id,
                context.getRevision(),
                AuditAction.PRIVATE_CONTEXT_UPDATED,
                "비공개 컨텍스트 수정 " + context.getKind() + " (값은 기록하지 않음)");
        return toResponse(context);
    }

    private KnowledgeSnippetResponse toResponse(KnowledgeSnippet s) {
        return KnowledgeSnippetResponse.from(s, json.readStrings(s.getFieldKeys()), json.readStrings(s.getDocTypes()));
    }

    private PrivateContextResponse toResponse(PrivateContext c) {
        return PrivateContextResponse.from(
                c,
                json.readStrings(c.getAliases()),
                json.readStringMap(c.getAttributes()),
                json.readStrings(c.getVisibleRoles()));
    }

    private void audit(
            MemberPrincipal actor, AuditTargetType type, Long id, long revision, AuditAction action, String summary) {
        auditRecorder.record(actor, type, String.valueOf(id), revision, action, null, summary);
    }

    private static List<String> distinct(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                        .filter(v -> v != null && !v.isBlank())
                        .map(String::strip)
                        .distinct()
                        .toList();
    }
}
