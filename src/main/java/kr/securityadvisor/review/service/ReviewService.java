package kr.securityadvisor.review.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.securityadvisor.assignment.dto.AssignmentResponse;
import kr.securityadvisor.assignment.dto.AssignmentSummary;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.auth.service.MemberDirectory;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.notification.domain.NotificationKind;
import kr.securityadvisor.notification.service.Notifier;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.DeadlineKind;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.AnswerBatchCommand;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.dto.DeadlineResponse;
import kr.securityadvisor.request.dto.SecurityRequestSummary;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import kr.securityadvisor.request.service.AnswerChangedEvent;
import kr.securityadvisor.request.service.AnswerService;
import kr.securityadvisor.request.service.DeadlineService;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.review.domain.Review;
import kr.securityadvisor.review.domain.SupplementStatus;
import kr.securityadvisor.review.dto.ReviewCompletionCommand;
import kr.securityadvisor.review.dto.ReviewInboxFilter;
import kr.securityadvisor.review.dto.ReviewInboxItem;
import kr.securityadvisor.review.dto.ReviewRequestCommand;
import kr.securityadvisor.review.dto.ReviewRequestResult;
import kr.securityadvisor.review.dto.ReviewResponse;
import kr.securityadvisor.review.dto.ReviewRevisionCommand;
import kr.securityadvisor.review.dto.StaffAnswerCommand;
import kr.securityadvisor.review.repository.ReviewRepository;
import kr.securityadvisor.review.repository.SupplementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 담당자 협의 유스케이스(F-04). 2단계 진입(협의 요청) → INFRA 할당·담당자 값 입력/확인 → 분야별 협의 완료 → 두 분야 완료 시 3단계.
 * 배정(F-12)·기한(F-13)·감사(F-14)는 같은 트랜잭션에서 함께 움직인다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final SupplementRepository supplementRepository;
    private final SecurityRequestRepository securityRequestRepository;
    private final BusinessCaseRepository businessCaseRepository;
    private final RequestAccessPolicy accessPolicy;
    private final ReviewerAccessPolicy reviewerAccessPolicy;
    private final PublicationService publicationService;
    private final AssignmentService assignmentService;
    private final DeadlineService deadlineService;
    private final AnswerService answerService;
    private final AuditRecorder auditRecorder;
    private final Notifier notifier;
    private final MemberDirectory memberDirectory;
    private final RequiredReviewFields requiredReviewFields;
    private final ReviewJson json;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;
    private final kr.securityadvisor.request.service.InfrastructurePlanService infrastructurePlans;
    private final kr.securityadvisor.request.service.ConsultationComparisonService consultationComparison;
    private final kr.securityadvisor.review.repository.StageReturnRepository stageReturns;

    /**
     * 신청자의 협의 요청: 1단계 작성 중 → 2단계 협의 중.
     *
     * @throws DomainRuleViolationException 단계가 맞지 않거나 서류 유형에 협의 분야가 없을 때(422)
     */
    public ReviewRequestResult requestReview(
            MemberPrincipal principal, Long securityRequestId, ReviewRequestCommand command) {
        SecurityRequest request = accessPolicy.ownedRequest(principal, securityRequestId);
        BusinessCase businessCase = accessPolicy.viewableCase(principal, request.getBusinessCaseId());
        request.requireRevision(command.expectedRevision());

        Set<ReviewField> fields = reviewFieldsOf(businessCase, request);
        request.requestReview();
        consultationComparison.baseline(principal, securityRequestId);

        List<ReviewResponse> reviews = fields.stream()
                .sorted()
                .map(field -> reviewRepository
                        .findBySecurityRequestIdAndField(securityRequestId, field)
                        .orElseGet(() -> reviewRepository.save(Review.open(securityRequestId, field))))
                .map(this::toResponse)
                .toList();

        List<Long> siblings = securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCase.getId()).stream()
                .map(SecurityRequest::getId)
                .filter(id -> !id.equals(securityRequestId))
                .toList();
        AssignmentSummary assignments = assignmentService.autoAssign(
                principal, securityRequestId, siblings, fields, businessCase.getApplicantUsername());
        List<DeadlineResponse> deadlines =
                deadlineService.startReviewDeadlines(principal, request, businessCase.getPublicationId(), fields);

        audit(
                principal,
                request,
                AuditAction.REVIEW_REQUESTED,
                null,
                "1단계 작성 중 → 2단계 협의 중. 협의 분야 " + fields
                        + (assignments.unassigned().isEmpty() ? "" : ", 미배정 " + assignments.unassigned()));
        notifier.notify(
                assignments.assigned().stream()
                        .map(AssignmentResponse::assigneeUsername)
                        .toList(),
                "request:" + securityRequestId + ":review-requested:" + request.getRevision(),
                NotificationKind.ASSIGNED,
                "신청 #" + securityRequestId + " 협의가 배정되었습니다",
                "/api/security-requests/" + securityRequestId);
        if (!assignments.unassigned().isEmpty()) {
            // 자동 배정 실패 → 기관 관리자에게(F-17). 재배정은 관리자가 사유와 함께 한다.
            notifier.notify(
                    memberDirectory.activeUsernamesWithRole(MemberRole.ADMIN),
                    "request:" + securityRequestId + ":unassigned:" + request.getRevision(),
                    NotificationKind.UNASSIGNED,
                    "신청 #" + securityRequestId + " " + assignments.unassigned() + " 분야 담당자가 배정되지 않았습니다 — 재배정 필요",
                    "/api/security-requests/" + securityRequestId + "/assignments");
        }
        return new ReviewRequestResult(SecurityRequestSummary.from(request), reviews, assignments, deadlines);
    }

    /** 협의 하나 조회(열람은 신청을 볼 수 있는 사람 누구나). */
    @Transactional(readOnly = true)
    public ReviewResponse get(MemberPrincipal principal, Long reviewId) {
        Review review = requireReview(reviewId);
        accessPolicy.viewableRequest(principal, review.getSecurityRequestId());
        return toResponse(review);
    }

    /** 신청의 분야별 협의 목록. */
    @Transactional(readOnly = true)
    public List<ReviewResponse> listForRequest(MemberPrincipal principal, Long securityRequestId) {
        accessPolicy.viewableRequest(principal, securityRequestId);
        return reviewRepository.findBySecurityRequestIdOrderByIdAsc(securityRequestId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 담당자 요청함: 내게 활성 배정된 신청의 협의 목록(배정 최근순)을 조건으로 거른다. ADMIN 은 배정이 없으므로 비어 있다. */
    @Transactional(readOnly = true)
    public List<ReviewInboxItem> inbox(MemberPrincipal principal, ReviewInboxFilter filter) {
        ReviewInboxFilter effective = filter == null ? ReviewInboxFilter.NONE : filter;
        return assignmentService.listMine(principal).stream()
                .map(assignment -> toInboxItem(principal, assignment))
                .flatMap(Optional::stream)
                .filter(effective::matches)
                .toList();
    }

    /**
     * 담당자 입력(STAFF_INPUT): 예) INFRA 가 서버·IP·포트를 할당값으로 적는다. 2단계 협의 중인 신청에서, 그 분야 배정자만.
     * 공통(scope=CASE) 항목이면 사업 건에 저장되어 같은 사업 건의 다른 신청에도 반영된다.
     */
    public AnswerResponse inputAnswer(
            MemberPrincipal principal, Long reviewId, String fieldKey, StaffAnswerCommand command) {
        Review review = requireReview(reviewId);
        SecurityRequest request = requireReviewableRequest(principal, review);
        AnswerResponse answer = answerService.inputByStaff(
                principal, request, fieldKey, command.value(), command.expectedRevision(), command.notApplicable());
        review.touch();
        return answer;
    }

    /** 여러 항목을 한 협의 트랜잭션에서 저장해 부분 저장과 자체 수정 충돌을 막는다. */
    public List<AnswerResponse> inputAnswers(MemberPrincipal principal, Long reviewId, AnswerBatchCommand command) {
        Review review = requireReview(reviewId);
        SecurityRequest request = requireReviewableRequest(principal, review);
        List<AnswerResponse> result = new java.util.ArrayList<>();
        for (AnswerBatchCommand.Item item : command.answers()) {
            result.add(answerService.inputByStaff(
                    principal, request, item.fieldKey(), item.value(), item.expectedRevision(), item.notApplicable()));
        }
        review.touch();
        return result;
    }

    /** 담당자 확인(STAFF_CONFIRMED): 사용자 답변·AI 추론 값이 맞다고 담당자가 확인한다. 값은 바뀌지 않는다. */
    public AnswerResponse confirmAnswer(
            MemberPrincipal principal, Long reviewId, String fieldKey, ReviewRevisionCommand command) {
        Review review = requireReview(reviewId);
        SecurityRequest request = requireReviewableRequest(principal, review);
        AnswerResponse answer = answerService.confirmByStaff(principal, request, fieldKey, command.expectedRevision());
        review.touch();
        return answer;
    }

    /** 이전 클라이언트의 분리된 할당 완료 요청은 상태 변경 없이 거부한다. 담당 분야 협의 완료로 통합되었다. */
    public ReviewResponse completeAllocation(MemberPrincipal principal, Long reviewId, ReviewRevisionCommand command) {
        Review review = requireReview(reviewId);
        requireReviewableRequest(principal, review);
        review.requireRevision(command.expectedRevision());
        throw new DomainRuleViolationException("자원 할당 완료는 담당 분야 협의 완료로 통합되었습니다");
    }

    /**
     * 분야 협의 완료(PUT …/completion). 체크리스트 전부 확인·의견 필수·미해결 보완 없음·SECURITY 는 INFRA 협의 완료 선행.
     * 필요한 분야가 모두 완료되면 신청을 3단계 사용자 최종 검토로 올리고 협의 기한을 종료한다.
     */
    public ReviewResponse complete(MemberPrincipal principal, Long reviewId, ReviewCompletionCommand command) {
        Review review = requireReview(reviewId);
        SecurityRequest request = requireReviewableRequest(principal, review);
        entityManager.refresh(request, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(review);
        requireReviewableRequest(principal, review);
        review.requireRevision(command.expectedRevision());

        List<Review> reviews = reviewRepository.findBySecurityRequestIdOrderByIdAsc(request.getId());
        // INFRA 선행(불변식 9)은 이 서류가 INFRA 협의를 요구할 때만 — SECURITY 만 요구하는 서류는 바로 완료할 수 있다
        boolean infraCompleted = !requiredReviewFields.infraPrecedes(requiredReviewFields.of(request))
                || reviews.stream()
                        .filter(other -> other.getField() == ReviewField.INFRA)
                        .anyMatch(Review::isCompleted);
        infrastructurePlans.requireCurrent(request);
        answerService.requireStaffAnswers(
                request, MemberRole.valueOf(review.getField().name()));
        int openSupplements = supplementRepository.countByReviewIdAndStatus(reviewId, SupplementStatus.OPEN);
        Instant now = clock.instant();
        review.complete(
                principal.username(),
                command.opinion(),
                command.checklist(),
                json.writeChecklist(command.checklist()),
                infraCompleted,
                openSupplements,
                now);
        supplementRepository
                .findByReviewIdAndStatus(reviewId, SupplementStatus.ANSWERED)
                .forEach(supplement -> supplement.resolve(principal.username(), now));
        audit(
                principal,
                request,
                AuditAction.REVIEW_COMPLETED,
                null,
                review.getField() + " 협의 완료 (협의 " + reviewId + ")");

        if (reviews.stream().allMatch(Review::isCompleted)) {
            request.completeReviews(true);
            notifier.notify(
                    businessCaseRepository
                            .findById(request.getBusinessCaseId())
                            .orElseThrow()
                            .getApplicantUsername(),
                    "request:" + request.getId() + ":reviews-completed:" + request.getRevision(),
                    NotificationKind.REVIEW_COMPLETED,
                    "신청 #" + request.getId() + " 담당자 협의가 완료되었습니다. 최종 초안을 확인해 주세요",
                    "/requests/" + request.getId());
            deadlineService.endActive(principal, request.getId(), DeadlineKind.REVIEW, now, "협의 완료");
            audit(principal, request, AuditAction.STAGE_ADVANCED, null, "2단계 협의 중 → 3단계 최종 검토 중 (필요한 분야 협의 완료)");
        }
        return toResponse(review);
    }

    /**
     * 사용자가 답변을 바꾸면(공통 항목이면 사업 건의 미제출 신청 전부) 완료된 협의를 재검토로 돌린다(불변식 7).
     * 3단계에서도 필수·기간·보안 조건·담당자 정보 변경은 2단계 재협의를 연다. 최종 초안 문장 편집과 같은 값 재저장은 제외한다.
     */
    @EventListener
    public void onAnswerChanged(AnswerChangedEvent event) {
        notifySubmittedReviewers(event);
        if (event.affectedRequestIds().isEmpty()) {
            return;
        }
        for (var request : securityRequestRepository.findAllById(event.affectedRequestIds())) {
            if (request.getStage() != 3 || request.isTerminal()) continue;
            var business =
                    businessCaseRepository.findById(request.getBusinessCaseId()).orElseThrow();
            var field = publicationService
                    .getFieldSchema(business.getPublicationId())
                    .find(event.fieldKey());
            boolean important = "infrastructure.plan".equals(event.fieldKey())
                    || field.filter(f -> f.appliesTo(request.getDocType()))
                            .map(f -> f.required()
                                    || f.isStaffField()
                                    || Set.of("BOOLEAN", "DATE_RANGE", "IP_ADDRESS", "PORT", "PROTOCOL")
                                            .contains(f.type()))
                            .orElse(false);
            if (!important) continue;
            String reason = "최종 검토 중 중요 정보 변경으로 담당자 재협의 필요: " + event.fieldKey();
            boolean supplementOpen =
                    request.getStatus() == kr.securityadvisor.request.domain.RequestStatus.SUPPLEMENT_REQUESTED;
            request.returnToStage(2, reason);
            if (supplementOpen) request.requestSupplement();
            stageReturns.save(kr.securityadvisor.review.domain.StageReturn.record(
                    request.getId(),
                    3,
                    2,
                    reason,
                    json.writeKeys(List.of(event.fieldKey())),
                    json.writeReasons(java.util.Map.of(event.fieldKey(), reason)),
                    event.actor().username(),
                    clock.instant()));
            deadlineService.startReviewDeadlines(
                    event.actor(), request, business.getPublicationId(), reviewFieldsOf(business, request));
            if (supplementOpen) {
                supplementRepository.findBySecurityRequestIdOrderByRequestedAtAscIdAsc(request.getId()).stream()
                        .filter(s -> s.getStatus() == SupplementStatus.OPEN)
                        .map(s -> s.getField())
                        .distinct()
                        .forEach(fieldWithSupplement -> deadlineService.pauseReviewDeadline(
                                event.actor(), request, fieldWithSupplement, business.getPublicationId()));
            }
            auditRecorder.record(
                    event.actor(),
                    AuditTargetType.SECURITY_REQUEST,
                    request.getId().toString(),
                    request.getRevision(),
                    AuditAction.STAGE_RETURNED,
                    reason,
                    "3단계 → 2단계 자동 재협의 · 기존 답변과 제출본 보존");
            notifier.notify(
                    business.getApplicantUsername(),
                    "material-change:" + request.getId() + ":" + java.util.UUID.randomUUID(),
                    NotificationKind.STAGE_RETURNED,
                    "신청 #" + request.getId() + " 중요 정보가 변경되어 담당자 재협의가 필요합니다",
                    "/requests/" + request.getId());
        }
        List<Long> inReview = securityRequestRepository.findAllById(event.affectedRequestIds()).stream()
                .filter(request -> request.getStage() == 2)
                .map(SecurityRequest::getId)
                .toList();
        if (inReview.isEmpty()) {
            return;
        }
        for (Review review : reviewRepository.findBySecurityRequestIdInOrderByIdAsc(inReview)) {
            if (review.isCompleted()) {
                review.markRecheck();
                boolean infrastructureHandoff = event.actor().role() == MemberRole.SECURITY
                        && event.fieldKey().equals("infrastructure.plan")
                        && review.getField() == ReviewField.INFRA;
                String reason = infrastructureHandoff
                        ? "정보보안 담당자가 서버·네트워크·운영 정보를 수정하여 ICT인프라 재협의가 필요합니다"
                        : "답변이 바뀌어 " + review.getField() + " 협의 재검토가 필요합니다";
                notifier.notify(
                        assignmentService.listForRequest(review.getSecurityRequestId()).stream()
                                .filter(a -> a.active() && a.field() == review.getField())
                                .map(AssignmentResponse::assigneeUsername)
                                .toList(),
                        "request:" + review.getSecurityRequestId() + ":recheck:" + review.getField() + ":"
                                + event.fieldKey() + ":" + java.util.UUID.randomUUID(),
                        NotificationKind.REVIEW_RECHECK,
                        "신청 #" + review.getSecurityRequestId() + " " + reason,
                        "/api/security-requests/" + review.getSecurityRequestId());
                auditRecorder.record(
                        event.actor(),
                        AuditTargetType.SECURITY_REQUEST,
                        String.valueOf(review.getSecurityRequestId()),
                        null,
                        AuditAction.REVIEW_RECHECK,
                        null,
                        reason + " — 항목 " + event.fieldKey() + " (협의 " + review.getId() + ")");
            }
        }
    }

    ReviewResponse toResponse(Review review) {
        return ReviewResponse.from(review, json.readChecklist(review.getChecklist()));
    }

    Review requireReview(Long reviewId) {
        return reviewRepository
                .findById(reviewId)
                .orElseThrow(() -> new NotFoundException("협의를 찾을 수 없습니다: " + reviewId));
    }

    /** 배정된 담당자인지 + 신청이 2단계 협의 중(보완 요청 포함)인지. */
    private SecurityRequest requireReviewableRequest(MemberPrincipal principal, Review review) {
        SecurityRequest request = accessPolicy.viewableRequest(principal, review.getSecurityRequestId());
        reviewerAccessPolicy.requireAssignedReviewer(principal, request.getId(), review.getField());
        if (request.getStage() != 2 || request.isTerminal()) {
            throw new DomainRuleViolationException("담당자 검토는 2단계 협의 중인 신청에서만 할 수 있습니다 (현재 " + request.getStage() + "단계 "
                    + request.getStatus().label() + ")");
        }
        return request;
    }

    /** 제출된 신청의 공통 정보가 바뀌면 그 담당자에게 알린다. 제출본은 불변이라 상태는 바꾸지 않는다(불변식 7·F-17). */
    private void notifySubmittedReviewers(AnswerChangedEvent event) {
        for (Long requestId : event.submittedRequestIds()) {
            notifier.notify(
                    assignmentService.listForRequest(requestId).stream()
                            .filter(AssignmentResponse::active)
                            .map(AssignmentResponse::assigneeUsername)
                            .toList(),
                    "request:" + requestId + ":common-info-changed:" + event.fieldKey() + ":"
                            + java.util.UUID.randomUUID(),
                    NotificationKind.COMMON_INFO_CHANGED,
                    "신청 #" + requestId + " 제출 뒤 공통 정보(" + event.fieldKey() + ")가 바뀌었습니다 — 제출본은 그대로입니다",
                    "/api/security-requests/" + requestId);
        }
    }

    private Optional<ReviewInboxItem> toInboxItem(MemberPrincipal principal, AssignmentResponse assignment) {
        return reviewRepository
                .findBySecurityRequestIdAndField(assignment.securityRequestId(), assignment.field())
                .map(review -> {
                    SecurityRequest request = securityRequestRepository
                            .findById(review.getSecurityRequestId())
                            .orElseThrow();
                    BusinessCase businessCase = businessCaseRepository
                            .findById(request.getBusinessCaseId())
                            .orElseThrow();
                    List<SecurityRequestSummary> siblings =
                            securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCase.getId()).stream()
                                    .filter(sibling -> !sibling.getId().equals(request.getId()))
                                    .filter(sibling -> accessPolicy.canViewRequest(principal, sibling))
                                    .map(SecurityRequestSummary::from)
                                    .toList();
                    return new ReviewInboxItem(
                            toResponse(review),
                            SecurityRequestSummary.from(request),
                            businessCase.getId(),
                            businessCase.getTitle(),
                            businessCase.getApplicantUsername(),
                            deadlineService.listForRequest(request.getId(), businessCase.getPublicationId()).stream()
                                    .filter(d -> d.active()
                                            && d.kind() == DeadlineKind.REVIEW
                                            && d.field() == review.getField())
                                    .map(DeadlineResponse::dueDate)
                                    .findFirst()
                                    .orElse(null),
                            supplementRepository.countBySecurityRequestIdAndStatus(
                                    request.getId(), SupplementStatus.OPEN),
                            siblings);
                });
    }

    private void audit(
            MemberPrincipal actor, SecurityRequest request, AuditAction action, String reason, String summary) {
        auditRecorder.record(
                actor,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(request.getId()),
                request.getRevision(),
                action,
                reason,
                summary);
    }

    /** 서류 유형이 요구하는 협의 분야(게시본 docTypes.reviewFields). 1차 범위는 INFRA·SECURITY 둘 다가 기본. */
    private Set<ReviewField> reviewFieldsOf(BusinessCase businessCase, SecurityRequest request) {
        return requiredReviewFields.of(businessCase, request);
    }
}
