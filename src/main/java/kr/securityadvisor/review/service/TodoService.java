package kr.securityadvisor.review.service;

import java.util.List;
import kr.securityadvisor.assignment.dto.AssignmentResponse;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.domain.Submission;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import kr.securityadvisor.request.repository.SubmissionRepository;
import kr.securityadvisor.review.domain.ReviewStatus;
import kr.securityadvisor.review.domain.SupplementStatus;
import kr.securityadvisor.review.dto.TodoSummary;
import kr.securityadvisor.review.repository.FinalConfirmationRepository;
import kr.securityadvisor.review.repository.ReviewRepository;
import kr.securityadvisor.review.repository.SupplementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "처리할 일 n건" 집계(F-17). 역할별로 지금 그 사람의 행위를 기다리는 건수만 센다 — 알림(읽음 여부)과 별개로 업무 상태에서 계산한다.
 * 1차 범위 규모(수백 건)에서는 조회 후 메모리 집계로 충분하다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TodoService {

    private final AssignmentService assignmentService;
    private final ReviewRepository reviewRepository;
    private final SupplementRepository supplementRepository;
    private final SubmissionRepository submissionRepository;
    private final FinalConfirmationRepository finalConfirmationRepository;
    private final SecurityRequestRepository securityRequestRepository;
    private final BusinessCaseRepository businessCaseRepository;
    private final RequiredReviewFields requiredReviewFields;

    public TodoSummary summarize(MemberPrincipal principal) {
        return switch (principal.role()) {
            case SECURITY, INFRA -> forReviewer(principal);
            case APPLICANT -> forApplicant(principal);
            case ADMIN -> forAdmin(principal);
        };
    }

    private TodoSummary forReviewer(MemberPrincipal principal) {
        long pendingReviews = 0;
        long pendingFinal = 0;
        for (AssignmentResponse assignment : assignmentService.listMine(principal)) {
            SecurityRequest request = securityRequestRepository
                    .findById(assignment.securityRequestId())
                    .orElse(null);
            if (request == null || request.isTerminal()) {
                continue;
            }
            if (request.getStage() == 2) {
                boolean completed = reviewRepository
                        .findBySecurityRequestIdAndField(request.getId(), assignment.field())
                        .map(review -> review.getStatus() == ReviewStatus.COMPLETED)
                        .orElse(false);
                if (!completed) {
                    pendingReviews++;
                }
            } else if (request.getStatus() == RequestStatus.SUBMITTED
                    && awaitsMyFinalConfirmation(request, assignment.field())) {
                pendingFinal++;
            }
        }
        List<Long> assignedIds = assignmentService.listMine(principal).stream()
                .map(AssignmentResponse::securityRequestId)
                .toList();
        long answered = assignedIds.isEmpty()
                ? 0
                : supplementRepository.countBySecurityRequestIdInAndFieldAndStatus(
                        assignedIds, ReviewField.valueOf(principal.role().name()), SupplementStatus.ANSWERED);
        return TodoSummary.of(principal.role(), pendingReviews, answered, pendingFinal, 0, 0, 0);
    }

    /** 최신 제출본에 내 분야 최종 확인이 없고, SECURITY 면 (INFRA 가 요구 분야일 때) INFRA 확인이 먼저 있어야 "지금 내 차례"다. */
    private boolean awaitsMyFinalConfirmation(SecurityRequest request, ReviewField field) {
        Submission latest = submissionRepository
                .findFirstBySecurityRequestIdOrderBySequenceNoDesc(request.getId())
                .orElse(null);
        if (latest == null || finalConfirmationRepository.existsBySubmissionIdAndField(latest.getId(), field)) {
            return false;
        }
        return field == ReviewField.INFRA
                || !requiredReviewFields.infraPrecedes(requiredReviewFields.of(request))
                || finalConfirmationRepository.existsBySubmissionIdAndField(latest.getId(), ReviewField.INFRA);
    }

    private TodoSummary forApplicant(MemberPrincipal principal) {
        List<Long> requestIds =
                businessCaseRepository.findByApplicantUsernameOrderByIdAsc(principal.username()).stream()
                        .map(BusinessCase::getId)
                        .flatMap(caseId -> securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(caseId).stream())
                        .filter(request -> !request.isTerminal())
                        .map(SecurityRequest::getId)
                        .toList();
        long openSupplements = requestIds.isEmpty()
                ? 0
                : supplementRepository.countBySecurityRequestIdInAndStatus(requestIds, SupplementStatus.OPEN);
        long finalReviews = requestIds.stream()
                .map(id -> securityRequestRepository.findById(id).orElseThrow())
                .filter(request -> request.getStatus() == RequestStatus.FINAL_REVIEW)
                .count();
        return TodoSummary.of(principal.role(), 0, 0, 0, openSupplements, finalReviews, 0);
    }

    private TodoSummary forAdmin(MemberPrincipal principal) {
        long unassigned = securityRequestRepository.findAll().stream()
                .filter(request -> request.getStage() >= 2 && !request.isTerminal())
                .filter(request -> requiredReviewFields.of(request).stream()
                        .anyMatch(field -> assignmentService.reassignmentReason(request.getId(), field) != null))
                .count();
        return TodoSummary.of(principal.role(), 0, 0, 0, 0, 0, unassigned);
    }
}
