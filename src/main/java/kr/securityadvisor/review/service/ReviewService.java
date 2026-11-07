package kr.securityadvisor.review.service;

import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kr.securityadvisor.assignment.dto.AssignmentSummary;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.DocTypeDefinition;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.DeadlineResponse;
import kr.securityadvisor.request.dto.SecurityRequestSummary;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import kr.securityadvisor.request.service.DeadlineService;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.review.domain.Review;
import kr.securityadvisor.review.dto.ReviewRequestCommand;
import kr.securityadvisor.review.dto.ReviewRequestResult;
import kr.securityadvisor.review.dto.ReviewResponse;
import kr.securityadvisor.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 담당자 협의 유스케이스(F-04·F-05). 이 단계에서는 2단계 진입(협의 요청)만 있다:
 * 신청 전이 → 분야별 협의 생성 → 자동 배정(F-12) → 협의 기한 생성(F-13) → 감사 이력(F-14), 전부 한 트랜잭션.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final SecurityRequestRepository securityRequestRepository;
    private final RequestAccessPolicy accessPolicy;
    private final PublicationService publicationService;
    private final AssignmentService assignmentService;
    private final DeadlineService deadlineService;
    private final AuditRecorder auditRecorder;

    /**
     * 신청자의 협의 요청: 1단계 작성 중 → 2단계 협의 중.
     *
     * @throws DomainRuleViolationException 단계가 맞지 않거나 서류 유형에 협의 분야가 없을 때(422)
     */
    public ReviewRequestResult requestReview(MemberPrincipal principal, Long securityRequestId, ReviewRequestCommand command) {
        SecurityRequest request = accessPolicy.ownedRequest(principal, securityRequestId);
        BusinessCase businessCase = accessPolicy.viewableCase(principal, request.getBusinessCaseId());
        request.requireRevision(command.expectedRevision());

        Set<ReviewField> fields = reviewFieldsOf(businessCase, request);
        request.requestReview();

        List<ReviewResponse> reviews = fields.stream()
                .sorted()
                .map(field -> reviewRepository
                        .findBySecurityRequestIdAndField(securityRequestId, field)
                        .orElseGet(() -> reviewRepository.save(Review.open(securityRequestId, field))))
                .map(ReviewResponse::from)
                .toList();

        List<Long> siblings = securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCase.getId()).stream()
                .map(SecurityRequest::getId)
                .filter(id -> !id.equals(securityRequestId))
                .toList();
        AssignmentSummary assignments = assignmentService.autoAssign(
                principal, securityRequestId, siblings, fields, businessCase.getApplicantUsername());
        List<DeadlineResponse> deadlines =
                deadlineService.startReviewDeadlines(principal, request, businessCase.getPublicationId(), fields);

        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(securityRequestId),
                request.getRevision(),
                AuditAction.REVIEW_REQUESTED,
                null,
                "1단계 작성 중 → 2단계 협의 중. 협의 분야 " + fields
                        + (assignments.unassigned().isEmpty() ? "" : ", 미배정 " + assignments.unassigned()));
        return new ReviewRequestResult(SecurityRequestSummary.from(request), reviews, assignments, deadlines);
    }

    /** 신청의 분야별 협의 목록. */
    @Transactional(readOnly = true)
    public List<ReviewResponse> listForRequest(MemberPrincipal principal, Long securityRequestId) {
        accessPolicy.viewableRequest(principal, securityRequestId);
        return reviewRepository.findBySecurityRequestIdOrderByIdAsc(securityRequestId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    /** 서류 유형이 요구하는 협의 분야(게시본 docTypes.reviewFields). 1차 범위는 INFRA·SECURITY 둘 다가 기본. */
    private Set<ReviewField> reviewFieldsOf(BusinessCase businessCase, SecurityRequest request) {
        DocTypeDefinition docType = publicationService
                .getFieldSchema(businessCase.getPublicationId())
                .findDocType(request.getDocType())
                .orElseThrow(() -> new DomainRuleViolationException(
                        "게시본에 없는 서류 유형입니다: " + request.getDocType()));
        Set<ReviewField> fields = docType.reviewFields() == null || docType.reviewFields().isEmpty()
                ? Set.of(ReviewField.INFRA, ReviewField.SECURITY)
                : docType.reviewFields().stream().map(ReviewField::of).collect(Collectors.toSet());
        if (fields.isEmpty()) {
            throw new DomainRuleViolationException("협의 분야가 없는 서류 유형입니다: " + request.getDocType());
        }
        return fields;
    }
}
