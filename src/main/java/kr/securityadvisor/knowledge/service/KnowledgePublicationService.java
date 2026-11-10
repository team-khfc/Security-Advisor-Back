package kr.securityadvisor.knowledge.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.json.JsonCodec;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.knowledge.domain.KnowledgeDraft;
import kr.securityadvisor.knowledge.domain.KnowledgePublication;
import kr.securityadvisor.knowledge.dto.KnowledgeComparisonResponse;
import kr.securityadvisor.knowledge.dto.KnowledgeDraftCommand;
import kr.securityadvisor.knowledge.dto.KnowledgeDraftResponse;
import kr.securityadvisor.knowledge.dto.KnowledgePublicationResponse;
import kr.securityadvisor.knowledge.dto.KnowledgeSnapshot;
import kr.securityadvisor.knowledge.dto.KnowledgeSnippetResponse;
import kr.securityadvisor.knowledge.dto.PrivateContextResponse;
import kr.securityadvisor.knowledge.repository.KnowledgeDraftRepository;
import kr.securityadvisor.knowledge.repository.KnowledgePublicationRepository;
import kr.securityadvisor.knowledge.repository.KnowledgeSnippetRepository;
import kr.securityadvisor.knowledge.repository.PrivateContextRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 초안 편집과 별개로 게시본을 만들며 이후 사업 생성 시 참조할 불변 ID를 제공한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class KnowledgePublicationService {
    private final KnowledgeDraftRepository drafts;
    private final KnowledgePublicationRepository publications;
    private final KnowledgeSnippetRepository snippets;
    private final PrivateContextRepository contexts;
    private final JsonCodec json;
    private final ObjectMapper mapper;
    private final AuditRecorder audit;
    private final Clock clock;

    public Long currentId() {
        return current().getId();
    }

    public KnowledgeSnapshot snapshot(Long id) {
        return mapper.readValue(require(id).getSnapshot(), KnowledgeSnapshot.class);
    }

    public KnowledgeDraftResponse draft(MemberPrincipal principal) {
        principal.requireAnyRole(MemberRole.ADMIN);
        return response(drafts.findById(1L).orElseThrow());
    }

    @Transactional
    public KnowledgeDraftResponse saveNote(MemberPrincipal principal, KnowledgeDraftCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        KnowledgeDraft draft = drafts.lockCurrent();
        draft.requireRevision(command.expectedRevision());
        draft.updateNote(command.note(), principal.username());
        drafts.flush();
        record(principal, draft, AuditAction.KNOWLEDGE_DRAFT_SAVED);
        return response(draft);
    }

    public KnowledgeComparisonResponse compare(MemberPrincipal principal) {
        principal.requireAnyRole(MemberRole.ADMIN);
        KnowledgeDraft draft = drafts.findById(1L).orElseThrow();
        KnowledgePublication current = current();
        recordRead(principal, "draft");
        return new KnowledgeComparisonResponse(
                current.getVersion(), null, draft.getRevision(), changes(snapshot(current.getId()), draftSnapshot()));
    }

    @Transactional
    public KnowledgePublicationResponse publish(MemberPrincipal principal, long expectedRevision) {
        principal.requireAnyRole(MemberRole.ADMIN);
        KnowledgeDraft draft = drafts.lockCurrent();
        draft.requireRevision(expectedRevision);
        KnowledgePublication current = current();
        KnowledgeSnapshot snapshot = draftSnapshot();
        if (changes(snapshot(current.getId()), snapshot).isEmpty())
            throw new DomainRuleViolationException("게시본과 다른 내용이 없어 게시할 수 없습니다");
        KnowledgePublication published = publications.save(KnowledgePublication.publish(
                current.getVersion() + 1,
                mapper.writeValueAsString(snapshot),
                clock.instant(),
                principal.username(),
                draft.getNote(),
                current.getVersion()));
        draft.published(published.getVersion(), principal.username());
        drafts.flush();
        record(principal, draft, AuditAction.KNOWLEDGE_PUBLISHED);
        return KnowledgePublicationResponse.from(published);
    }

    public PageResponse<KnowledgePublicationResponse> history(MemberPrincipal principal, int page, int size) {
        principal.requireAnyRole(MemberRole.ADMIN);
        page(page, size);
        return PageResponse.from(
                publications.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "version"))),
                KnowledgePublicationResponse::from);
    }

    public KnowledgeSnapshot publishedContent(MemberPrincipal principal, Long id) {
        principal.requireAnyRole(MemberRole.ADMIN);
        recordRead(principal, "publication:" + id);
        return snapshot(id);
    }

    public KnowledgeComparisonResponse publishedComparison(MemberPrincipal principal, Long id) {
        principal.requireAnyRole(MemberRole.ADMIN);
        KnowledgePublication target = require(id);
        recordRead(principal, "publication:" + id);
        if (target.getPreviousVersion() == null)
            return new KnowledgeComparisonResponse(target.getVersion(), target.getVersion(), null, List.of());
        KnowledgePublication before =
                publications.findByVersion(target.getPreviousVersion()).orElseThrow();
        return new KnowledgeComparisonResponse(
                before.getVersion(), target.getVersion(), null, changes(snapshot(before.getId()), snapshot(id)));
    }

    public PageResponse<KnowledgeSnippetResponse> snippetDrafts(
            MemberPrincipal principal, String query, int page, int size) {
        principal.requireAnyRole(MemberRole.ADMIN);
        page(page, size);
        return PageResponse.from(
                snippets.searchDraft(query == null ? "" : query.strip(), PageRequest.of(page, size)),
                s -> KnowledgeSnippetResponse.from(
                        s, json.readStrings(s.getFieldKeys()), json.readStrings(s.getDocTypes())));
    }

    public PageResponse<PrivateContextResponse> contextDrafts(
            MemberPrincipal principal, String query, int page, int size) {
        principal.requireAnyRole(MemberRole.ADMIN);
        page(page, size);
        recordRead(principal, "draft");
        return PageResponse.from(
                contexts.searchDraft(query == null ? "" : query.strip(), PageRequest.of(page, size)),
                c -> PrivateContextResponse.from(
                        c,
                        json.readStrings(c.getAliases()),
                        json.readStringMap(c.getAttributes()),
                        json.readStrings(c.getVisibleRoles())));
    }

    private KnowledgeSnapshot draftSnapshot() {
        return new KnowledgeSnapshot(
                snippets.findAllByOrderByIdAsc().stream()
                        .map(s -> KnowledgeSnippetResponse.from(
                                s, json.readStrings(s.getFieldKeys()), json.readStrings(s.getDocTypes())))
                        .toList(),
                contexts.findAllByOrderByIdAsc().stream()
                        .map(c -> PrivateContextResponse.from(
                                c,
                                json.readStrings(c.getAliases()),
                                json.readStringMap(c.getAttributes()),
                                json.readStrings(c.getVisibleRoles())))
                        .toList());
    }

    public KnowledgeSnippetResponse snippetDraft(MemberPrincipal principal, Long id) {
        principal.requireAnyRole(MemberRole.ADMIN);
        var s = snippets.findById(id).orElseThrow(() -> new NotFoundException("공개 지식 초안을 찾을 수 없습니다"));
        return KnowledgeSnippetResponse.from(s, json.readStrings(s.getFieldKeys()), json.readStrings(s.getDocTypes()));
    }

    public PrivateContextResponse contextDraft(MemberPrincipal principal, Long id) {
        principal.requireAnyRole(MemberRole.ADMIN);
        recordRead(principal, "draft:" + id);
        var c = contexts.findById(id).orElseThrow(() -> new NotFoundException("비공개 정보 초안을 찾을 수 없습니다"));
        return PrivateContextResponse.from(
                c,
                json.readStrings(c.getAliases()),
                json.readStringMap(c.getAttributes()),
                json.readStrings(c.getVisibleRoles()));
    }

    private KnowledgeDraftResponse response(KnowledgeDraft d) {
        return new KnowledgeDraftResponse(
                d.getId(),
                d.getBasedOnVersion(),
                current().getVersion(),
                d.getRevision(),
                d.getNote(),
                d.getUpdatedBy(),
                d.getUpdatedAt(),
                snippets.count(),
                contexts.count());
    }

    private KnowledgePublication current() {
        return publications.findFirstByOrderByVersionDesc().orElseThrow(() -> new NotFoundException("지식 게시본이 없습니다"));
    }

    private KnowledgePublication require(Long id) {
        return publications.findById(id).orElseThrow(() -> new NotFoundException("지식 게시본을 찾을 수 없습니다"));
    }

    private void record(MemberPrincipal actor, KnowledgeDraft d, AuditAction action) {
        audit.record(
                actor,
                AuditTargetType.KNOWLEDGE_PUBLICATION,
                d.getId().toString(),
                d.getRevision(),
                action,
                null,
                "지식 초안·게시 처리 — 원문과 변경 메모는 기록하지 않음");
    }

    private void recordRead(MemberPrincipal actor, String id) {
        audit.recordAccess(
                actor,
                AuditTargetType.PRIVATE_CONTEXT,
                id,
                AuditAction.PRIVATE_CONTEXT_VIEWED,
                "지식 관리 사본 조회 — 원문은 기록하지 않음");
    }

    private static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("페이지는 0 이상, 크기는 1~100이어야 합니다");
    }

    private static List<KnowledgeComparisonResponse.Change> changes(KnowledgeSnapshot before, KnowledgeSnapshot after) {
        List<KnowledgeComparisonResponse.Change> changes = new ArrayList<>();
        Map<Long, KnowledgeSnippetResponse> oldSnippets = new LinkedHashMap<>();
        before.snippets().forEach(s -> oldSnippets.put(s.knowledgeSnippetId(), s));
        for (var item : after.snippets()) {
            var prior = oldSnippets.remove(item.knowledgeSnippetId());
            add(changes, "PUBLIC", item.knowledgeSnippetId(), item.sourceTitle(), describe(prior), describe(item));
        }
        oldSnippets
                .values()
                .forEach(s -> add(changes, "PUBLIC", s.knowledgeSnippetId(), s.sourceTitle(), describe(s), "없음"));
        Map<Long, PrivateContextResponse> oldContexts = new LinkedHashMap<>();
        before.privateContexts().forEach(c -> oldContexts.put(c.privateContextId(), c));
        for (var item : after.privateContexts()) {
            var prior = oldContexts.remove(item.privateContextId());
            add(changes, "PRIVATE", item.privateContextId(), item.name(), describe(prior), describe(item));
        }
        oldContexts.values().forEach(c -> add(changes, "PRIVATE", c.privateContextId(), c.name(), describe(c), "없음"));
        return List.copyOf(changes);
    }

    private static void add(
            List<KnowledgeComparisonResponse.Change> changes,
            String kind,
            Long id,
            String label,
            String before,
            String after) {
        if (!before.equals(after)) changes.add(new KnowledgeComparisonResponse.Change(kind, id, label, before, after));
    }

    private static String describe(KnowledgeSnippetResponse s) {
        if (s == null) return "없음";
        return "자료명: " + s.sourceTitle() + "\n판본: " + s.sourceVersion() + "\n위치: " + s.sourceLocator()
                + "\n적용 질문: " + new TreeSet<>(s.fieldKeys()) + "\n적용 서류: "
                + s.docTypes().stream()
                        .sorted()
                        .map(KnowledgePublicationService::label)
                        .toList()
                + "\n사용: " + (s.active() ? "예" : "아니요") + "\n" + s.body();
    }

    private static String describe(PrivateContextResponse c) {
        if (c == null) return "없음";
        return "종류: " + label(c.kind().name()) + "\n이름: " + c.name() + "\n별칭: " + new TreeSet<>(c.aliases())
                + "\n속성: " + new TreeMap<>(c.attributes()) + "\n열람 역할: "
                + c.visibleRoles().stream()
                        .sorted()
                        .map(KnowledgePublicationService::label)
                        .toList()
                + "\n사용: " + (c.active() ? "예" : "아니요") + "\n메모: " + Objects.toString(c.note(), "");
    }

    private static String label(String code) {
        return switch (code) {
            case "SERVER" -> "서버";
            case "NETWORK_ZONE" -> "망 구역";
            case "ASSET" -> "자산";
            case "NOTE" -> "기관 참고 정보";
            case "INFRA" -> "ICT인프라";
            case "SECURITY" -> "정보보안";
            case "ADMIN" -> "기관 관리자";
            case "FIREWALL" -> "방화벽 정책 허용";
            case "SECURITY_REVIEW" -> "정보화사업 보안성검토";
            default -> code;
        };
    }
}
