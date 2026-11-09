package kr.securityadvisor.request.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.ValueSource;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.domain.*;
import kr.securityadvisor.request.dto.FinalDraftCommand;
import kr.securityadvisor.request.dto.FinalDraftResponse;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import kr.securityadvisor.request.repository.FinalDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 최종 초안 확인과 제출 검사. LLM 호출은 이 컨텍스트 밖에서 수행한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class FinalDraftService {
    private final FinalDraftRepository repository;
    private final BusinessCaseRepository businessCases;
    private final RequestAccessPolicy access;
    private final SubmissionChecker checker;
    private final AuditRecorder audit;
    private final ObjectMapper mapper;
    private final EntityManager entityManager;
    private final Clock clock;

    /** 모델 호출 전의 답변 기준을 만들고 호출 후에도 같은 기준인지 확인한다. */
    public String prepare(MemberPrincipal actor, Long requestId, long expectedRevision) {
        SecurityRequest request = editable(actor, requestId);
        request.requireRevision(expectedRevision);
        return generationBase(request);
    }

    /** 직접 작성도 명시적인 확인을 거치므로 AI 장애 시 같은 절차를 쓸 수 있다. */
    public FinalDraftResponse createManual(MemberPrincipal actor, Long requestId, FinalDraftCommand command) {
        String fingerprint = prepare(actor, requestId, command.expectedRevision());
        return propose(actor, requestId, fingerprint, command.text(), ValueSource.USER_ANSWER, null);
    }

    public FinalDraftResponse propose(
            MemberPrincipal actor, Long requestId, String fingerprint, String text, ValueSource source, String model) {
        SecurityRequest request = editable(actor, requestId);
        entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        editable(actor, requestId);
        if (!fingerprint.equals(generationBase(request)))
            throw new ConflictException("작성 중 원본 답변이 변경되었습니다. 최신 답변으로 다시 작성해 주세요.");
        FinalDraft draft =
                repository.saveAndFlush(FinalDraft.propose(requestId, fingerprint(request), text, source, model));
        audit.record(
                actor,
                AuditTargetType.SECURITY_REQUEST,
                requestId.toString(),
                request.getRevision(),
                AuditAction.FINAL_DRAFT_CREATED,
                null,
                "최종 초안 " + draft.getId() + " 생성 · 확인 전");
        return response(draft, fingerprint(request));
    }

    @Transactional(readOnly = true)
    public FinalDraftResponse current(MemberPrincipal actor, Long requestId) {
        SecurityRequest request = access.viewableRequest(actor, requestId);
        return repository
                .findFirstBySecurityRequestIdOrderByIdDesc(requestId)
                .map(d -> response(d, fingerprint(request)))
                .orElse(null);
    }

    public FinalDraftResponse confirm(MemberPrincipal actor, Long draftId, FinalDraftCommand command) {
        FinalDraft draft = repository.findById(draftId).orElseThrow(() -> new NotFoundException("최종 초안을 찾을 수 없습니다."));
        SecurityRequest request = editable(actor, draft.getSecurityRequestId());
        entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        editable(actor, request.getId());
        FinalDraft latest = repository
                .findFirstBySecurityRequestIdOrderByIdDesc(request.getId())
                .orElseThrow();
        if (!latest.getId().equals(draftId)) throw new ConflictException("새 초안이 있습니다. 최신 초안을 확인해 주세요.");
        String fingerprint = fingerprint(request);
        draft.confirm(command.text(), actor.username(), clock.instant(), command.expectedRevision(), fingerprint);
        repository.flush();
        audit.record(
                actor,
                AuditTargetType.SECURITY_REQUEST,
                request.getId().toString(),
                request.getRevision(),
                AuditAction.FINAL_DRAFT_CONFIRMED,
                null,
                "최종 초안 " + draftId + " 사용자 확인");
        return response(draft, fingerprint);
    }

    /** 이전 제출본은 바꾸지 않으며 진행 중인 초안의 확인만 무효화한다. */
    @EventListener
    public void answersChanged(AnswerChangedEvent event) {
        for (Long id : event.affectedRequestIds())
            repository.findFirstBySecurityRequestIdOrderByIdDesc(id).ifPresent(FinalDraft::invalidate);
    }

    /** 기존 서술 없는 제출은 호환 유지. 한 번 생성한 서술은 반드시 최신·확인 상태여야 한다. */
    public Map<String, Object> submissionSnapshot(SecurityRequest request) {
        FinalDraft draft = repository
                .findFirstBySecurityRequestIdOrderByIdDesc(request.getId())
                .orElse(null);
        if (draft == null) return null;
        entityManager.lock(draft, LockModeType.OPTIMISTIC);
        draft.requireSubmittable(fingerprint(request));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("finalDraftId", draft.getId());
        result.put("text", draft.getText());
        result.put("originalText", draft.getOriginalText());
        result.put("valueSource", draft.getValueSource().name());
        result.put("modelReference", draft.getModelReference());
        result.put("confirmedBy", draft.getConfirmedBy());
        result.put("confirmedAt", draft.getConfirmedAt().toString());
        result.put("sourceFingerprint", draft.getSourceFingerprint());
        return result;
    }

    private SecurityRequest editable(MemberPrincipal actor, Long requestId) {
        SecurityRequest request = access.ownedRequest(actor, requestId);
        if (request.getStage() != 3 || request.getStatus() != RequestStatus.FINAL_REVIEW)
            throw new DomainRuleViolationException("최종 검토 단계에서만 최종 초안을 작성·확인할 수 있습니다.");
        return request;
    }

    private String generationBase(SecurityRequest request) {
        return fingerprint(request) + ":"
                + repository
                        .findFirstBySecurityRequestIdOrderByIdDesc(request.getId())
                        .map(draft -> draft.getId() + ":" + draft.getRevision())
                        .orElse("0:0");
    }

    private String fingerprint(SecurityRequest request) {
        BusinessCase businessCase =
                businessCases.findById(request.getBusinessCaseId()).orElseThrow();
        var answers = checker.answersByKey(businessCase, request).values().stream()
                .sorted(Comparator.comparing(Answer::getFieldKey))
                .map(a -> a.getFieldKey() + ":" + a.getId() + ":" + a.getRevision())
                .toList();
        String input = mapper.writeValueAsString(java.util.List.of(
                businessCase.getPublicationId(), businessCase.getRevision(), request.getRevision(), answers));
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", impossible);
        }
    }

    private FinalDraftResponse response(FinalDraft draft, String fingerprint) {
        return new FinalDraftResponse(
                draft.getId(),
                draft.getSecurityRequestId(),
                draft.getText(),
                draft.getValueSource().name(),
                draft.getModelReference(),
                draft.getConfirmedBy(),
                draft.getConfirmedAt(),
                draft.isConfirmed(),
                draft.isStale(fingerprint),
                draft.getRevision());
    }
}
