package kr.securityadvisor.review.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.ForbiddenException;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.notification.domain.NotificationKind;
import kr.securityadvisor.notification.service.Notifier;
import kr.securityadvisor.request.domain.DeadlineKind;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.domain.Submission;
import kr.securityadvisor.request.dto.SecurityRequestSummary;
import kr.securityadvisor.request.service.DeadlineService;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.request.service.SubmissionService;
import kr.securityadvisor.review.domain.FinalConfirmation;
import kr.securityadvisor.review.dto.ConfirmationCommand;
import kr.securityadvisor.review.dto.FinalConfirmationCommand;
import kr.securityadvisor.review.dto.FinalConfirmationResponse;
import kr.securityadvisor.review.repository.FinalConfirmationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4단계 최종 확인·확정(F-07). 제출본을 INFRA 가 먼저, SECURITY 가 다음으로 각자 최종 확인하고, 두 분야 확인이 끝나면
 * 배정된 SECURITY 담당자가 확정한다(불변식 34). 확정은 종료 상태(불변식 35)이며 방화벽 실제 적용이 아니다.
 * 방화벽 규칙 생성(불변식 26)은 확정 이벤트(RequestConfirmedEvent)를 받은 policy 컨텍스트(F-15)가 같은 트랜잭션에서 한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FinalConfirmationService {

    private final FinalConfirmationRepository finalConfirmationRepository;
    private final SubmissionService submissionService;
    private final RequestAccessPolicy accessPolicy;
    private final ReviewerAccessPolicy reviewerAccessPolicy;
    private final DeadlineService deadlineService;
    private final AuditRecorder auditRecorder;
    private final Notifier notifier;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** 분야 최종 확인(POST /api/submissions/{id}/final-confirmations). 호출자의 역할이 분야를 정한다. */
    public FinalConfirmationResponse confirmFinal(
            MemberPrincipal principal, Long submissionId, FinalConfirmationCommand command) {
        ReviewField field = fieldOf(principal);
        Submission submission = submissionService.requireSubmission(submissionId);
        SecurityRequest request = requireSubmittedLatest(principal, submission);
        reviewerAccessPolicy.requireAssignedReviewer(principal, request.getId(), field);
        request.requireRevision(command.expectedRevision());

        FinalConfirmation confirmation = finalConfirmationRepository.save(FinalConfirmation.confirm(
                submissionId,
                request.getId(),
                field,
                command.opinion(),
                principal.username(),
                finalConfirmationRepository.existsBySubmissionIdAndField(submissionId, ReviewField.INFRA),
                finalConfirmationRepository.existsBySubmissionIdAndField(submissionId, field),
                clock.instant()));
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(request.getId()),
                request.getRevision(),
                AuditAction.FINAL_CONFIRMED,
                null,
                field + " 최종 확인 (제출본 " + submissionId + ")");
        return FinalConfirmationResponse.from(confirmation);
    }

    /** 확정(PUT /api/submissions/{id}/confirmation). 배정된 SECURITY 담당자만, 두 분야 최종 확인 이후. 멱등: 이미 확정이면 그대로. */
    public SecurityRequestSummary confirm(MemberPrincipal principal, Long submissionId, ConfirmationCommand command) {
        Submission submission = submissionService.requireSubmission(submissionId);
        SecurityRequest request = accessPolicy.viewableRequest(principal, submission.getSecurityRequestId());
        reviewerAccessPolicy.requireAssignedReviewer(principal, request.getId(), ReviewField.SECURITY);
        if (!submissionService.isLatest(submission)) {
            throw new DomainRuleViolationException("최신 제출본이 아닙니다: " + submissionId);
        }
        request.requireRevision(command.expectedRevision());
        boolean both = finalConfirmationRepository.existsBySubmissionIdAndField(submissionId, ReviewField.INFRA)
                && finalConfirmationRepository.existsBySubmissionIdAndField(submissionId, ReviewField.SECURITY);
        request.confirm(both);
        Instant now = clock.instant();
        deadlineService.endActive(principal, request.getId(), DeadlineKind.FINAL_CONFIRMATION, now, "확정");
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(request.getId()),
                request.getRevision(),
                AuditAction.REQUEST_CONFIRMED,
                null,
                "4단계 제출됨 → 확정 (제출본 " + submissionId + "). 서류 확정 ≠ 방화벽 적용");
        eventPublisher.publishEvent(
                new RequestConfirmedEvent(principal, request.getId(), submissionId, request.getDocType()));
        notifier.notify(
                accessPolicy
                        .viewableCase(principal, request.getBusinessCaseId())
                        .getApplicantUsername(),
                "request:" + request.getId() + ":confirmed",
                NotificationKind.REQUEST_CONFIRMED,
                "신청 #" + request.getId() + " 서류가 확정되었습니다(방화벽 적용은 별도)",
                "/api/submissions/" + submissionId);
        return SecurityRequestSummary.from(request);
    }

    /** 제출본의 최종 확인 목록. */
    @Transactional(readOnly = true)
    public List<FinalConfirmationResponse> list(MemberPrincipal principal, Long submissionId) {
        Submission submission = submissionService.requireSubmission(submissionId);
        accessPolicy.viewableRequest(principal, submission.getSecurityRequestId());
        return finalConfirmationRepository.findBySubmissionIdOrderByConfirmedAtAscIdAsc(submissionId).stream()
                .map(FinalConfirmationResponse::from)
                .toList();
    }

    private SecurityRequest requireSubmittedLatest(MemberPrincipal principal, Submission submission) {
        SecurityRequest request = accessPolicy.viewableRequest(principal, submission.getSecurityRequestId());
        if (request.getStage() != 4 || request.isTerminal()) {
            throw new DomainRuleViolationException("최종 확인은 4단계 제출됨 상태의 신청에서만 할 수 있습니다 (현재 " + request.getStage() + "단계 "
                    + request.getStatus().label() + ")");
        }
        if (!submissionService.isLatest(submission)) {
            throw new DomainRuleViolationException("최신 제출본이 아닙니다: " + submission.getId());
        }
        return request;
    }

    private static ReviewField fieldOf(MemberPrincipal principal) {
        if (principal.hasRole(MemberRole.INFRA)) {
            return ReviewField.INFRA;
        }
        if (principal.hasRole(MemberRole.SECURITY)) {
            return ReviewField.SECURITY;
        }
        throw new ForbiddenException("최종 확인은 INFRA·SECURITY 담당자만 할 수 있습니다");
    }
}
