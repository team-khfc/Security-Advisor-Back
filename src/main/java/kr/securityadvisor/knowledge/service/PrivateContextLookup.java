package kr.securityadvisor.knowledge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.knowledge.dto.PrivateContextResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사업에 고정된 사실을 조회하되 현재 게시본에서 철회한 열람 권한은 과거 사본에도 적용한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PrivateContextLookup {
    private final KnowledgePublicationService publications;
    private final AuditRecorder auditRecorder;

    public List<FactRecord> matchIn(MemberPrincipal principal, List<String> texts) {
        return matchIn(principal, texts, publications.currentId());
    }

    public List<FactRecord> matchIn(MemberPrincipal principal, List<String> texts, Long publicationId) {
        if (principal.hasRole(MemberRole.APPLICANT) || texts == null || texts.isEmpty()) return List.of();
        String haystack = String.join("\n", texts).toLowerCase(Locale.ROOT);
        return visibleTo(principal, publicationId).stream()
                .filter(context -> {
                    List<String> names = new ArrayList<>(context.aliases());
                    names.add(context.name());
                    return names.stream()
                            .filter(n -> n != null && !n.isBlank())
                            .anyMatch(n -> haystack.contains(n.toLowerCase(Locale.ROOT)));
                })
                .map(this::toFact)
                .toList();
    }

    public List<FactRecord> suggest(MemberPrincipal principal, String query) {
        return suggest(principal, query, publications.currentId());
    }

    public List<FactRecord> suggest(MemberPrincipal principal, String query, Long publicationId) {
        if (principal.hasRole(MemberRole.APPLICANT)) return List.of();
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return visibleTo(principal, publicationId).stream()
                .filter(context -> q.isEmpty()
                        || context.name().toLowerCase(Locale.ROOT).contains(q)
                        || context.aliases().stream()
                                .anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(q)))
                .map(this::toFact)
                .toList();
    }

    public List<String> knownValues(MemberPrincipal principal) {
        return knownValues(principal, publications.currentId());
    }

    public List<String> knownValues(MemberPrincipal principal, Long publicationId) {
        return visibleTo(principal, publicationId).stream()
                .flatMap(context -> context.attributes().values().stream())
                .toList();
    }

    private List<PrivateContextResponse> visibleTo(MemberPrincipal principal, Long publicationId) {
        if (principal.hasRole(MemberRole.APPLICANT)) return List.of();
        String role = principal.role().name();
        Map<Long, PrivateContextResponse> latest =
                publications.snapshot(publications.currentId()).privateContexts().stream()
                        .collect(Collectors.toMap(PrivateContextResponse::privateContextId, Function.identity()));
        List<PrivateContextResponse> visible = publications.snapshot(publicationId).privateContexts().stream()
                .filter(context -> {
                    PrivateContextResponse current = latest.get(context.privateContextId());
                    return context.active()
                            && current != null
                            && current.active()
                            && (principal.hasRole(MemberRole.ADMIN)
                                    || (context.visibleRoles().contains(role)
                                            && current.visibleRoles().contains(role)));
                })
                .toList();
        auditRecorder.recordAccess(
                principal,
                AuditTargetType.PRIVATE_CONTEXT,
                "*",
                AuditAction.PRIVATE_CONTEXT_VIEWED,
                "게시본 " + publicationId + " 비공개 정보 조회 " + visible.size() + "건 — 원문은 기록하지 않음");
        return visible;
    }

    private FactRecord toFact(PrivateContextResponse context) {
        return new FactRecord(context.privateContextId(), context.kind(), context.name(), context.attributes());
    }
}
