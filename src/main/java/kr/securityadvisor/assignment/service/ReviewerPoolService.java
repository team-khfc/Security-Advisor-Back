package kr.securityadvisor.assignment.service;

import java.util.List;
import java.util.Optional;
import kr.securityadvisor.assignment.domain.Assignment;
import kr.securityadvisor.assignment.domain.ReviewerPool;
import kr.securityadvisor.assignment.dto.ReviewerPoolActivationCommand;
import kr.securityadvisor.assignment.dto.ReviewerPoolDeactivationResult;
import kr.securityadvisor.assignment.dto.ReviewerPoolRegisterCommand;
import kr.securityadvisor.assignment.dto.ReviewerPoolResponse;
import kr.securityadvisor.assignment.repository.AssignmentRepository;
import kr.securityadvisor.assignment.repository.ReviewerPoolRepository;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.auth.service.MemberDirectory;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분야별 담당자 목록 관리(F-12, 기관 관리자). 등록·기본 담당자 지정·비활성화. 분야당 활성 기본 담당자는 1명(부분 유일 인덱스).
 *
 * <p>비활성화는 배정을 바꾸지 않는다 — 그 사람의 활성 배정이 남은 신청을 "재배정 필요"로 돌려주고 감사 이력에 남긴다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReviewerPoolService {

    private final ReviewerPoolRepository reviewerPoolRepository;
    private final AssignmentRepository assignmentRepository;
    private final MemberDirectory memberDirectory;
    private final ReviewerAvailability availability;
    private final AuditRecorder auditRecorder;
    private final org.springframework.context.ApplicationEventPublisher events;

    /** 목록(분야·ID순). field 를 주면 그 분야만. 비활성 등록도 함께 돌려준다(active 로 구분). */
    @Transactional(readOnly = true)
    public List<ReviewerPoolResponse> list(MemberPrincipal admin, ReviewField field) {
        admin.requireAnyRole(MemberRole.ADMIN);
        List<ReviewerPool> pools = field == null
                ? reviewerPoolRepository.findAllByOrderByFieldAscIdAsc()
                : reviewerPoolRepository.findByFieldOrderByIdAsc(field);
        return pools.stream().map(this::response).toList();
    }

    /**
     * 담당자 등록. 이미 비활성으로 남아 있던 등록은 다시 활성화한다(분야·계정 유일 제약).
     *
     * @throws DomainRuleViolationException 계정이 없거나 비활성이거나 분야 역할이 아닐 때, 이미 활성 등록일 때(422)
     */
    public ReviewerPoolResponse register(MemberPrincipal admin, ReviewerPoolRegisterCommand command) {
        admin.requireAnyRole(MemberRole.ADMIN);
        memberDirectory.requireActiveWithRole(
                command.memberUsername(), command.field().role());
        Optional<ReviewerPool> existing =
                reviewerPoolRepository.findByFieldAndMemberUsername(command.field(), command.memberUsername());
        if (existing.isPresent() && existing.get().isActive()) {
            throw new DomainRuleViolationException(
                    command.memberUsername() + " 은(는) 이미 " + command.field() + " 분야의 활성 담당자입니다");
        }
        ReviewerPool pool = existing.orElseGet(
                () -> reviewerPoolRepository.save(ReviewerPool.register(command.field(), command.memberUsername())));
        pool.reactivate();
        auditRecorder.record(
                admin,
                AuditTargetType.REVIEWER_POOL,
                String.valueOf(pool.getId()),
                null,
                AuditAction.REVIEWER_POOL_ADDED,
                null,
                command.field() + " 담당자 등록: " + command.memberUsername());
        if (command.defaultReviewer()) {
            makeDefault(admin, pool);
        }
        return response(pool);
    }

    /** 기본 담당자 지정(PUT …/{id}/default). 이전 기본 담당자는 해제된다. 멱등. */
    public ReviewerPoolResponse setDefault(MemberPrincipal admin, Long poolId) {
        admin.requireAnyRole(MemberRole.ADMIN);
        ReviewerPool pool = requirePool(poolId);
        if (!pool.isActive()) {
            throw new DomainRuleViolationException("비활성 담당자는 기본 담당자로 지정할 수 없습니다");
        }
        memberDirectory.requireActiveWithRole(
                pool.getMemberUsername(), pool.getField().role());
        if (!pool.isDefaultReviewer()) {
            makeDefault(admin, pool);
        }
        return response(pool);
    }

    /**
     * 활성/비활성 설정(PUT …/{id}/activation). 비활성화하면 기본 담당자 지정도 풀리고, 그 사람의 활성 배정이 남은 신청을
     * 재배정 필요 목록으로 돌려준다. 다시 활성화하면 기본 담당자는 아니다. 멱등.
     */
    public ReviewerPoolDeactivationResult setActivation(
            MemberPrincipal admin, Long poolId, ReviewerPoolActivationCommand command) {
        admin.requireAnyRole(MemberRole.ADMIN);
        ReviewerPool pool = requirePool(poolId);
        if (command.active()) {
            if (!pool.isActive()) {
                memberDirectory.requireActiveWithRole(
                        pool.getMemberUsername(), pool.getField().role());
                pool.reactivate();
                auditRecorder.record(
                        admin,
                        AuditTargetType.REVIEWER_POOL,
                        String.valueOf(pool.getId()),
                        null,
                        AuditAction.REVIEWER_POOL_ADDED,
                        command.reason(),
                        pool.getField() + " 담당자 재활성화: " + pool.getMemberUsername());
            }
            return new ReviewerPoolDeactivationResult(response(pool), List.of());
        }
        List<Long> needReassignment =
                assignmentRepository
                        .findByAssigneeUsernameAndFieldAndActiveTrue(pool.getMemberUsername(), pool.getField())
                        .stream()
                        .map(Assignment::getSecurityRequestId)
                        .distinct()
                        .sorted()
                        .toList();
        if (pool.isActive()) {
            boolean wasDefault = pool.isDefaultReviewer();
            pool.deactivate();
            events.publishEvent(new kr.securityadvisor.common.schema.ReviewerEligibilityChanged(
                    pool.getMemberUsername(), java.util.UUID.randomUUID().toString()));
            auditRecorder.record(
                    admin,
                    AuditTargetType.REVIEWER_POOL,
                    String.valueOf(pool.getId()),
                    null,
                    AuditAction.REVIEWER_POOL_DEACTIVATED,
                    command.reason(),
                    pool.getField() + " 담당자 비활성화: " + pool.getMemberUsername()
                            + (wasDefault ? " (기본 담당자 해제 — 새 기본 담당자 지정 필요)" : "")
                            + (needReassignment.isEmpty() ? "" : " — 재배정 필요 신청 " + needReassignment));
        }
        return new ReviewerPoolDeactivationResult(response(pool), needReassignment);
    }

    private void makeDefault(MemberPrincipal admin, ReviewerPool pool) {
        Optional<ReviewerPool> previous =
                reviewerPoolRepository.findByFieldAndDefaultReviewerTrueAndActiveTrue(pool.getField());
        previous.ifPresent(ReviewerPool::unmarkDefault);
        // 부분 유일 인덱스(분야당 활성 기본 1명) 때문에 이전 기본 해제 UPDATE 를 먼저 내보낸다.
        reviewerPoolRepository.flush();
        pool.markDefault();
        auditRecorder.record(
                admin,
                AuditTargetType.REVIEWER_POOL,
                String.valueOf(pool.getId()),
                null,
                AuditAction.REVIEWER_POOL_DEFAULT_CHANGED,
                null,
                pool.getField() + " 기본 담당자: "
                        + previous.map(ReviewerPool::getMemberUsername).orElse("(없음)") + " → "
                        + pool.getMemberUsername());
    }

    private ReviewerPoolResponse response(ReviewerPool pool) {
        return ReviewerPoolResponse.from(pool, availability.reason(pool.getMemberUsername(), pool.getField()));
    }

    private ReviewerPool requirePool(Long poolId) {
        return reviewerPoolRepository
                .findById(poolId)
                .orElseThrow(() -> new NotFoundException("담당자 목록 항목을 찾을 수 없습니다: " + poolId));
    }
}
