package kr.securityadvisor.review.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.notification.domain.NotificationKind;
import kr.securityadvisor.notification.service.Notifier;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.DeadlineKind;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.service.AnswerService;
import kr.securityadvisor.request.service.DeadlineService;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.review.domain.Review;
import kr.securityadvisor.review.domain.Supplement;
import kr.securityadvisor.review.domain.SupplementStatus;
import kr.securityadvisor.review.dto.SupplementAnswerCommand;
import kr.securityadvisor.review.dto.SupplementRequestCommand;
import kr.securityadvisor.review.dto.SupplementResponse;
import kr.securityadvisor.review.repository.ReviewRepository;
import kr.securityadvisor.review.repository.SupplementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보완 요청·답변(F-05). 단계는 그대로 두고 상태만 보완 요청 ↔ 원래 상태를 오간다.
 * 보완 요청은 대상 항목을 재검토 표시하고(값 보존) 보완 응답 기한을 새로 만든다(불변식 11·23).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SupplementService {

    private final SupplementRepository supplementRepository;
    private final ReviewRepository reviewRepository;
    private final RequestAccessPolicy accessPolicy;
    private final ReviewerAccessPolicy reviewerAccessPolicy;
    private final AnswerService answerService;
    private final DeadlineService deadlineService;
    private final AuditRecorder auditRecorder;
    private final Notifier notifier;
    private final ReviewJson json;
    private final Clock clock;

    /** 담당자의 보완 요청(POST /api/reviews/{id}/supplements). 2단계 협의 중 또는 3단계 최종 검토 중인 신청에서, 그 분야 배정자만. */
    public SupplementResponse request(MemberPrincipal principal, Long reviewId, SupplementRequestCommand command) {
        Review review = reviewRepository
                .findById(reviewId)
                .orElseThrow(() -> new NotFoundException("협의를 찾을 수 없습니다: " + reviewId));
        SecurityRequest request = accessPolicy.viewableRequest(principal, review.getSecurityRequestId());
        reviewerAccessPolicy.requireAssignedReviewer(principal, request.getId(), review.getField());
        review.requireRevision(command.expectedRevision());
        BusinessCase businessCase = accessPolicy.viewableCase(principal, request.getBusinessCaseId());

        List<String> fieldKeys = command.fieldKeys().stream().distinct().toList();
        Instant now = clock.instant();
        request.requestSupplement();
        review.requestSupplement();
        answerService.markNeedsReview(request, fieldKeys);
        Supplement supplement = supplementRepository.save(Supplement.request(
                request.getId(),
                reviewId,
                review.getField(),
                json.writeKeys(fieldKeys),
                !fieldKeys.isEmpty(),
                command.reason(),
                principal.username(),
                now));
        deadlineService.restartSupplementDeadline(principal, request, businessCase.getPublicationId());
        // 신청자 답변을 기다리는 동안 이 분야 담당자의 협의 기한은 멈춘다(F-13)
        deadlineService.pauseReviewDeadline(principal, request, review.getField(), businessCase.getPublicationId());
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(request.getId()),
                request.getRevision(),
                AuditAction.SUPPLEMENT_REQUESTED,
                command.reason(),
                review.getField() + " 보완 요청 " + fieldKeys + " (보완 " + supplement.getId() + ")");
        notifier.notify(
                businessCase.getApplicantUsername(),
                "supplement:" + supplement.getId() + ":requested",
                NotificationKind.SUPPLEMENT_REQUESTED,
                "신청 #" + request.getId() + " 보완 요청이 도착했습니다",
                "/api/security-requests/" + request.getId() + "/supplements");
        return SupplementResponse.from(supplement, fieldKeys);
    }

    /**
     * 신청자의 보완 답변(POST /api/supplements/{id}/answers). 항목 값은 답변 API 로 먼저 고친 뒤 호출한다.
     * 이 협의에 미해결 보완이 더 없으면 협의·신청 상태가 원래대로 돌아가고 보완 응답 기한을 종료한다.
     */
    public SupplementResponse answer(MemberPrincipal principal, Long supplementId, SupplementAnswerCommand command) {
        Supplement supplement = supplementRepository
                .findById(supplementId)
                .orElseThrow(() -> new NotFoundException("보완 요청을 찾을 수 없습니다: " + supplementId));
        SecurityRequest request = accessPolicy.ownedRequest(principal, supplement.getSecurityRequestId());
        supplement.requireRevision(command.expectedRevision());
        Instant now = clock.instant();
        supplement.answer(command.note(), now);

        Review review = reviewRepository.findById(supplement.getReviewId()).orElseThrow();
        if (supplementRepository.countByReviewIdAndStatus(review.getId(), SupplementStatus.OPEN) == 0) {
            review.onSupplementAnswered();
            // 담당자 검토 재개 → 정지해 둔 협의 기한을 남은 영업일로 다시 만든다(F-13)
            BusinessCase businessCase = accessPolicy.viewableCase(principal, request.getBusinessCaseId());
            deadlineService.resumeReviewDeadline(
                    principal, request, review.getField(), businessCase.getPublicationId());
        }
        if (supplementRepository.countBySecurityRequestIdAndStatus(request.getId(), SupplementStatus.OPEN) == 0) {
            request.answerSupplement();
            deadlineService.endActive(principal, request.getId(), DeadlineKind.SUPPLEMENT_RESPONSE, now, "보완 답변");
        }
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(request.getId()),
                request.getRevision(),
                AuditAction.SUPPLEMENT_ANSWERED,
                null,
                supplement.getField() + " 보완 답변 (보완 " + supplementId + ")");
        notifier.notify(
                supplement.getRequestedBy(),
                "supplement:" + supplementId + ":answered",
                NotificationKind.SUPPLEMENT_ANSWERED,
                "신청 #" + request.getId() + " 보완 답변이 등록되었습니다",
                "/api/security-requests/" + request.getId() + "/supplements");
        return SupplementResponse.from(supplement, json.readKeys(supplement.getTargetFieldKeys()));
    }

    /**
     * 담당자 해소(PUT /api/supplements/{id}/resolution): 답변된 보완을 검토가 끝났다고 표시한다. 협의 완료 시 일괄 해소되기도 하지만,
     * 담당자가 미리 정리할 수 있게 둔 명시적 경로. 그 분야 배정 담당자만. 멱등: 이미 해소면 그대로.
     */
    public SupplementResponse resolve(MemberPrincipal principal, Long supplementId, SupplementAnswerCommand command) {
        Supplement supplement = supplementRepository
                .findById(supplementId)
                .orElseThrow(() -> new NotFoundException("보완 요청을 찾을 수 없습니다: " + supplementId));
        SecurityRequest request = accessPolicy.viewableRequest(principal, supplement.getSecurityRequestId());
        reviewerAccessPolicy.requireAssignedReviewer(principal, request.getId(), supplement.getField());
        if (supplement.getStatus() == SupplementStatus.RESOLVED) {
            return SupplementResponse.from(supplement, json.readKeys(supplement.getTargetFieldKeys()));
        }
        supplement.requireRevision(command.expectedRevision());
        supplement.resolve(principal.username(), clock.instant());
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(request.getId()),
                request.getRevision(),
                AuditAction.SUPPLEMENT_RESOLVED,
                command.note(),
                supplement.getField() + " 보완 해소 (보완 " + supplementId + ")");
        return SupplementResponse.from(supplement, json.readKeys(supplement.getTargetFieldKeys()));
    }

    /** 신청의 보완 요청 목록(요청순). */
    @Transactional(readOnly = true)
    public List<SupplementResponse> listForRequest(MemberPrincipal principal, Long securityRequestId) {
        accessPolicy.viewableRequest(principal, securityRequestId);
        return supplementRepository.findBySecurityRequestIdOrderByRequestedAtAscIdAsc(securityRequestId).stream()
                .map(supplement -> SupplementResponse.from(supplement, json.readKeys(supplement.getTargetFieldKeys())))
                .toList();
    }
}
