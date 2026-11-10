package kr.securityadvisor.request.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.securityadvisor.assignment.dto.AssignmentResponse;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.notification.domain.NotificationKind;
import kr.securityadvisor.notification.service.Notifier;
import kr.securityadvisor.request.domain.Answer;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.domain.Submission;
import kr.securityadvisor.request.domain.SubmissionPreconditions;
import kr.securityadvisor.request.dto.DocumentBoxItem;
import kr.securityadvisor.request.dto.SubmissionResponse;
import kr.securityadvisor.request.dto.SubmissionSummary;
import kr.securityadvisor.request.dto.SubmitCommand;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import kr.securityadvisor.request.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 제출·제출본 조회·문서함(F-07). 제출은 3단계 최종 검토 중인 신청에서 신청자만, 제출 전 검사(불변식 1·2·3)를 전부 통과해야 한다.
 * 제출본은 그 시점의 공통 값 + 서류별 값 + 설정 버전의 불변 스냅샷이며, 멱등성 키로 중복 제출을 막는다(불변식 4).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SubmissionService {

    private static final int SNAPSHOT_FORMAT = 1;

    private final SubmissionRepository submissionRepository;
    private final SecurityRequestRepository securityRequestRepository;
    private final BusinessCaseRepository businessCaseRepository;
    private final RequestAccessPolicy accessPolicy;
    private final SubmissionChecker submissionChecker;
    private final FinalDraftService finalDraftService;
    private final EntityManager entityManager;
    private final ReviewFactsProvider reviewFacts;
    private final DeadlineService deadlineService;
    private final AuditRecorder auditRecorder;
    private final Notifier notifier;
    private final AssignmentService assignmentService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 제출(POST /api/security-requests/{id}/submissions). 같은 멱등성 키로 다시 오면 이미 만든 제출본을 그대로 돌려준다.
     *
     * @return 제출본과, 이번 호출로 새로 만들어졌는지
     */
    public SubmitResult submit(
            MemberPrincipal principal, Long securityRequestId, String idempotencyKey, SubmitCommand command) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new BadRequestException("Idempotency-Key 헤더가 필요합니다(100자 이내)");
        }
        SecurityRequest request = accessPolicy.ownedRequest(principal, securityRequestId);
        entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        Submission existing =
                submissionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getSecurityRequestId().equals(securityRequestId)) {
                throw new ConflictException("다른 신청에 쓰인 Idempotency-Key 입니다");
            }
            return new SubmitResult(toResponse(existing), false);
        }
        request.requireRevision(command.expectedRevision());
        BusinessCase businessCase = accessPolicy.viewableCase(principal, request.getBusinessCaseId());

        SubmissionPreconditions preconditions = submissionChecker.check(
                businessCase,
                request,
                reviewFacts.openSupplementCount(securityRequestId),
                reviewFacts.allReviewsCompleted(securityRequestId));
        Map<String, Object> finalDraft = finalDraftService.submissionSnapshot(request);
        request.submit(preconditions);

        Instant now = clock.instant();
        Map<String, Answer> answers = submissionChecker.answersByKey(businessCase, request);
        Submission submission = submissionRepository.save(Submission.of(
                securityRequestId,
                businessCase.getPublicationId(),
                submissionRepository.countBySecurityRequestId(securityRequestId) + 1,
                snapshotJson(businessCase, request, answers, now, finalDraft),
                idempotencyKey,
                principal.username(),
                now));
        deadlineService.startFinalConfirmationDeadline(principal, request, businessCase.getPublicationId());
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(securityRequestId),
                request.getRevision(),
                AuditAction.SUBMITTED,
                null,
                "3단계 최종 검토 중 → 4단계 제출됨 (제출본 " + submission.getId() + ", 개정 " + submission.getSequenceNo() + ")");
        notifier.notify(
                assignmentService.listForRequest(securityRequestId).stream()
                        .filter(AssignmentResponse::active)
                        .map(AssignmentResponse::assigneeUsername)
                        .toList(),
                "submission:" + submission.getId() + ":submitted",
                NotificationKind.SUBMITTED,
                "신청 #" + securityRequestId + " 제출본(개정 " + submission.getSequenceNo() + ") 최종 확인이 필요합니다",
                "/api/submissions/" + submission.getId());
        return new SubmitResult(toResponse(submission), true);
    }

    /** 제출본 상세(스냅샷 포함). 열람은 신청을 볼 수 있는 사람. */
    @Transactional(readOnly = true)
    public SubmissionResponse get(MemberPrincipal principal, Long submissionId) {
        Submission submission = requireSubmission(submissionId);
        accessPolicy.viewableRequest(principal, submission.getSecurityRequestId());
        return toResponse(submission);
    }

    /** 신청의 제출본 목록(최근 개정 먼저). */
    @Transactional(readOnly = true)
    public List<SubmissionSummary> listForRequest(MemberPrincipal principal, Long securityRequestId) {
        accessPolicy.viewableRequest(principal, securityRequestId);
        List<Submission> submissions =
                submissionRepository.findBySecurityRequestIdOrderBySequenceNoDesc(securityRequestId);
        return submissions.stream()
                .map(s -> SubmissionSummary.from(s, s == submissions.getFirst()))
                .toList();
    }

    /**
     * 문서함(GET /api/submissions): 역할·권한 범위 내 제출본. 신청자는 자기 사업 건, 담당자는 활성 배정, 관리자는 전체.
     * 필터는 서류 유형·신청 상태·최신 개정만. (1차 범위: 메모리 페이징 — 제출본 수가 적은 시연 규모 기준)
     */
    @Transactional(readOnly = true)
    public List<DocumentBoxItem> documentBox(
            MemberPrincipal principal, String docType, String status, boolean latestOnly) {
        List<Submission> submissions = principal.hasRole(MemberRole.APPLICANT)
                ? submissionRepository.findAllForApplicant(principal.username())
                : submissionRepository.findAllByOrderBySubmittedAtDescIdDesc();
        Map<Long, SecurityRequest> requests = new LinkedHashMap<>();
        Map<Long, BusinessCase> cases = new LinkedHashMap<>();
        Map<Long, Long> latestByRequest = new LinkedHashMap<>();
        for (Submission s : submissions) {
            latestByRequest.merge(s.getSecurityRequestId(), s.getId(), Math::max);
        }
        return submissions.stream()
                .filter(s -> accessPolicy.canViewRequest(principal, s.getSecurityRequestId()))
                .map(s -> {
                    SecurityRequest request = requests.computeIfAbsent(
                            s.getSecurityRequestId(),
                            id -> securityRequestRepository.findById(id).orElseThrow());
                    BusinessCase businessCase = cases.computeIfAbsent(
                            request.getBusinessCaseId(),
                            id -> businessCaseRepository.findById(id).orElseThrow());
                    boolean latest =
                            latestByRequest.get(s.getSecurityRequestId()).equals(s.getId());
                    return new DocumentBoxItem(
                            s.getId(),
                            s.getSequenceNo(),
                            latest,
                            request.getId(),
                            request.getDocType(),
                            request.getStage(),
                            request.getStatus(),
                            request.getStatus().label(),
                            businessCase.getId(),
                            businessCase.getTitle(),
                            businessCase.getApplicantUsername(),
                            s.getSubmittedBy(),
                            s.getSubmittedAt());
                })
                .filter(item -> docType == null
                        || docType.isBlank()
                        || item.docType().code().equals(docType))
                .filter(item -> status == null
                        || status.isBlank()
                        || item.status().name().equals(status))
                .filter(item -> !latestOnly || item.latest())
                .toList();
    }

    /** 다른 컨텍스트(review)가 쓰는 조회: 제출본과 그 신청. */
    @Transactional(readOnly = true)
    public Submission requireSubmission(Long submissionId) {
        return submissionRepository
                .findById(submissionId)
                .orElseThrow(() -> new NotFoundException("제출본을 찾을 수 없습니다: " + submissionId));
    }

    /** 이 제출본이 신청의 최신 개정본인지(4→3 반환 후 재제출되면 이전 제출본에는 최종 확인·확정을 할 수 없다). */
    @Transactional(readOnly = true)
    public boolean isLatest(Submission submission) {
        return submissionRepository
                .findFirstBySecurityRequestIdOrderBySequenceNoDesc(submission.getSecurityRequestId())
                .map(latest -> latest.getId().equals(submission.getId()))
                .orElse(false);
    }

    private SubmissionResponse toResponse(Submission submission) {
        JsonNode snapshot = objectMapper.readTree(submission.getSnapshot());
        return SubmissionResponse.from(submission, isLatest(submission), snapshot);
    }

    /** 스냅샷: 값·출처·확인자까지 담는다(제출본 불변의 근거). AI 근거 원문은 담지 않고 참조만(불변식 25 준용). */
    private String snapshotJson(
            BusinessCase businessCase,
            SecurityRequest request,
            Map<String, Answer> answers,
            Instant now,
            Map<String, Object> finalDraft) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", SNAPSHOT_FORMAT);
        root.put("businessCaseId", businessCase.getId());
        root.put("caseTitle", businessCase.getTitle());
        root.put("securityRequestId", request.getId());
        root.put("docType", request.getDocType().code());
        root.put("publicationId", businessCase.getPublicationId());
        root.put("knowledgePublicationId", businessCase.getKnowledgePublicationId());
        root.put("submittedAt", now.toString());
        List<Map<String, Object>> values = answers.values().stream()
                .map(answer -> {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("fieldKey", answer.getFieldKey());
                    v.put("scope", answer.getScope().name());
                    v.put("value", answer.getValue());
                    v.put("notApplicable", answer.isNotApplicable());
                    v.put(
                            "valueSource",
                            answer.getValueSource() == null
                                    ? null
                                    : answer.getValueSource().name());
                    v.put("confirmedBy", answer.getConfirmedBy());
                    v.put(
                            "confirmedAt",
                            answer.getConfirmedAt() == null
                                    ? null
                                    : answer.getConfirmedAt().toString());
                    return v;
                })
                .toList();
        root.put("answers", values);
        if (finalDraft != null) root.put("finalDraft", finalDraft);
        return objectMapper.writeValueAsString(root);
    }

    /** 제출 결과 — created 가 false 면 같은 멱등성 키의 이전 제출본(200). */
    public record SubmitResult(SubmissionResponse submission, boolean created) {}
}
