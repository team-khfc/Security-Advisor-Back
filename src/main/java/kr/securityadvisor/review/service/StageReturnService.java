package kr.securityadvisor.review.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.DeadlineKind;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.SecurityRequestSummary;
import kr.securityadvisor.request.service.AnswerService;
import kr.securityadvisor.request.service.DeadlineService;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.review.domain.Review;
import kr.securityadvisor.review.domain.StageReturn;
import kr.securityadvisor.review.dto.ClosureCommand;
import kr.securityadvisor.review.dto.StageReturnCommand;
import kr.securityadvisor.review.dto.StageReturnResponse;
import kr.securityadvisor.review.repository.ReviewRepository;
import kr.securityadvisor.review.repository.StageReturnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차수 반환·종결(F-05). 둘 다 사유 필수, 기존 값·이력 보존(불변식 6·13). 반환은 지정 항목만 "보완 필요"로 강조하고
 * 완료된 협의를 재검토로 돌린다. 신청 단위 행위라서 그 신청에 배정된 담당자(어느 분야든)만 할 수 있다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class StageReturnService {

    private final StageReturnRepository stageReturnRepository;
    private final ReviewRepository reviewRepository;
    private final RequestAccessPolicy accessPolicy;
    private final ReviewerAccessPolicy reviewerAccessPolicy;
    private final AnswerService answerService;
    private final DeadlineService deadlineService;
    private final AuditRecorder auditRecorder;
    private final ReviewJson json;
    private final Clock clock;

    /** 차수 반환(POST /api/security-requests/{id}/stage-returns). 허용 반환 2→1, 3→2, 3→1, 4→3. */
    public StageReturnResponse returnToStage(
            MemberPrincipal principal, Long securityRequestId, StageReturnCommand command) {
        SecurityRequest request = accessPolicy.viewableRequest(principal, securityRequestId);
        reviewerAccessPolicy.requireAnyAssignedReviewer(principal, securityRequestId);
        request.requireRevision(command.expectedRevision());
        BusinessCase businessCase = accessPolicy.viewableCase(principal, request.getBusinessCaseId());

        int fromStage = request.getStage();
        List<String> fieldKeys = command.fieldKeys() == null
                ? List.of()
                : command.fieldKeys().stream().distinct().toList();
        Instant now = clock.instant();
        request.returnToStage(command.targetStage(), command.reason());
        answerService.markNeedsReview(request, fieldKeys);
        reviewRepository.findBySecurityRequestIdOrderByIdAsc(securityRequestId).forEach(Review::markRecheck);
        deadlineService.endActive(principal, securityRequestId, DeadlineKind.REVIEW, now, "차수 반환");
        deadlineService.endActive(principal, securityRequestId, DeadlineKind.FINAL_CONFIRMATION, now, "차수 반환");
        deadlineService.restartSupplementDeadline(principal, request, businessCase.getPublicationId());
        StageReturn stageReturn = stageReturnRepository.save(StageReturn.record(
                securityRequestId,
                fromStage,
                command.targetStage(),
                command.reason(),
                json.writeKeys(fieldKeys),
                principal.username(),
                now));
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(securityRequestId),
                request.getRevision(),
                AuditAction.STAGE_RETURNED,
                command.reason(),
                fromStage + "단계 → " + command.targetStage() + "단계 반환, 보완 필요 항목 " + fieldKeys);
        return StageReturnResponse.from(stageReturn, fieldKeys);
    }

    /** 신청의 차수 반환 이력(최근순). */
    @Transactional(readOnly = true)
    public List<StageReturnResponse> listForRequest(MemberPrincipal principal, Long securityRequestId) {
        accessPolicy.viewableRequest(principal, securityRequestId);
        return stageReturnRepository.findBySecurityRequestIdOrderByReturnedAtDescIdDesc(securityRequestId).stream()
                .map(stageReturn ->
                        StageReturnResponse.from(stageReturn, json.readKeys(stageReturn.getTargetFieldKeys())))
                .toList();
    }

    /** 종결(반려) — PUT /api/security-requests/{id}/closure. 모든 활성 기한을 종료한다. 같은 사업 건의 다른 신청은 건드리지 않는다(불변식 7). */
    public SecurityRequestSummary close(MemberPrincipal principal, Long securityRequestId, ClosureCommand command) {
        SecurityRequest request = accessPolicy.viewableRequest(principal, securityRequestId);
        reviewerAccessPolicy.requireAnyAssignedReviewer(principal, securityRequestId);
        request.requireRevision(command.expectedRevision());
        request.close(command.reason());
        Instant now = clock.instant();
        for (DeadlineKind kind : DeadlineKind.values()) {
            deadlineService.endActive(principal, securityRequestId, kind, now, "종결");
        }
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(securityRequestId),
                request.getRevision(),
                AuditAction.REQUEST_CLOSED,
                command.reason(),
                "담당자 종결(반려) — " + request.getStage() + "단계에서 종료");
        return SecurityRequestSummary.from(request);
    }
}
