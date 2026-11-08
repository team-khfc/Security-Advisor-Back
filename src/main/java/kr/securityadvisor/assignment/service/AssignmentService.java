package kr.securityadvisor.assignment.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.securityadvisor.assignment.domain.Assignment;
import kr.securityadvisor.assignment.dto.AssignmentResponse;
import kr.securityadvisor.assignment.dto.AssignmentSummary;
import kr.securityadvisor.assignment.dto.ReassignRequest;
import kr.securityadvisor.assignment.repository.AssignmentRepository;
import kr.securityadvisor.assignment.repository.ReviewerPoolRepository;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.auth.service.MemberDirectory;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 담당자 배정 유스케이스(F-12). 다른 컨텍스트(review)는 신청 ID 와 이 서비스의 공개 메서드로만 배정을 다룬다.
 *
 * <p>자동 배정 규칙(3-3장): 분야별 기본 담당자, 같은 사업 건의 다른 신청이 이미 배정돼 있으면 같은 담당자 우선,
 * 기본 담당자가 없거나 신청자 본인이면 미배정. 재배정은 기관 관리자만, 사유 필수, 이전 배정은 비활성으로 보존.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final ReviewerPoolRepository reviewerPoolRepository;
    private final MemberDirectory memberDirectory;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    /**
     * 2단계 진입 시 분야별 자동 배정.
     *
     * @param securityRequestId 배정할 신청
     * @param siblingRequestIds 같은 사업 건의 다른 신청(같은 담당자 우선용)
     * @param fields 서류 유형이 요구하는 협의 분야
     * @param applicantUsername 신청자(이해충돌 차단)
     */
    public AssignmentSummary autoAssign(
            MemberPrincipal actor,
            Long securityRequestId,
            List<Long> siblingRequestIds,
            Set<ReviewField> fields,
            String applicantUsername) {
        Instant now = clock.instant();
        List<AssignmentResponse> assigned = new ArrayList<>();
        List<ReviewField> unassigned = new ArrayList<>();
        for (ReviewField field : fields) {
            if (assignmentRepository
                    .findBySecurityRequestIdAndFieldAndActiveTrue(securityRequestId, field)
                    .isPresent()) {
                continue;
            }
            Optional<String> candidate = preferredSibling(siblingRequestIds, field)
                    .or(() -> reviewerPoolRepository
                            .findByFieldAndDefaultReviewerTrueAndActiveTrue(field)
                            .map(pool -> pool.getMemberUsername()))
                    .filter(username -> !username.equals(applicantUsername))
                    // 담당자 목록에 남아 있어도 계정이 비활성이거나 역할이 바뀌었으면 배정하지 않는다(F-11 역할 변경 반영).
                    .filter(username -> memberDirectory.isActiveWithRole(username, field.role()));
            if (candidate.isEmpty()) {
                unassigned.add(field);
                auditRecorder.record(
                        actor,
                        AuditTargetType.SECURITY_REQUEST,
                        String.valueOf(securityRequestId),
                        null,
                        AuditAction.UNASSIGNED,
                        null,
                        field + " 분야에 배정 가능한 기본 담당자 없음 — 미배정(기관 관리자 재배정 필요)");
                continue;
            }
            Assignment assignment = assignmentRepository.save(
                    Assignment.autoAssign(securityRequestId, field, candidate.get(), applicantUsername, now));
            auditRecorder.record(
                    actor,
                    AuditTargetType.SECURITY_REQUEST,
                    String.valueOf(securityRequestId),
                    null,
                    AuditAction.ASSIGNED,
                    null,
                    field + " 담당자 자동 배정 → " + candidate.get() + " (배정 " + assignment.getId() + ")");
            assigned.add(AssignmentResponse.from(assignment));
        }
        return new AssignmentSummary(assigned, unassigned);
    }

    /**
     * 기관 관리자의 재배정(또는 미배정 분야의 수동 배정).
     *
     * @throws DomainRuleViolationException 담당자 목록에 없는 사람·신청자 본인·사유 없음(422)
     */
    public AssignmentResponse reassign(
            MemberPrincipal admin, Long securityRequestId, String applicantUsername, ReassignRequest request) {
        admin.requireAnyRole(MemberRole.ADMIN);
        reviewerPoolRepository
                .findByFieldAndMemberUsernameAndActiveTrue(request.field(), request.assigneeUsername())
                .orElseThrow(() -> new DomainRuleViolationException(
                        request.assigneeUsername() + " 은(는) " + request.field() + " 분야의 활성 담당자가 아닙니다"));
        memberDirectory.requireActiveWithRole(
                request.assigneeUsername(), request.field().role());
        if (request.assigneeUsername().equals(applicantUsername)) {
            throw new DomainRuleViolationException("신청자 본인에게는 배정할 수 없습니다");
        }
        Instant now = clock.instant();
        Optional<Assignment> previous =
                assignmentRepository.findBySecurityRequestIdAndFieldAndActiveTrue(securityRequestId, request.field());
        previous.ifPresent(assignment -> assignment.deactivate(now));
        // 부분 유일 인덱스(신청·분야당 활성 1건) 때문에 새 행 INSERT 전에 이전 행의 비활성 UPDATE 를 먼저 내보낸다.
        assignmentRepository.flush();
        Assignment created = assignmentRepository.save(Assignment.reassign(
                securityRequestId,
                request.field(),
                request.assigneeUsername(),
                applicantUsername,
                admin.username(),
                request.reason(),
                now));
        auditRecorder.record(
                admin,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(securityRequestId),
                null,
                AuditAction.REASSIGNED,
                request.reason(),
                request.field() + " 담당자 재배정: "
                        + previous.map(Assignment::getAssigneeUsername).orElse("(미배정)") + " → "
                        + request.assigneeUsername() + " (배정 " + created.getId() + ")");
        return AssignmentResponse.from(created);
    }

    /** 신청의 배정 이력(활성·비활성 모두). */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> listForRequest(Long securityRequestId) {
        return assignmentRepository.findBySecurityRequestIdOrderByAssignedAtAscIdAsc(securityRequestId).stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    /** 검토 행위 권한 검사용: 이 사람이 해당 분야의 활성 담당자인가(불변식 19). */
    @Transactional(readOnly = true)
    public boolean isActiveAssignee(Long securityRequestId, ReviewField field, String username) {
        return assignmentRepository
                .findBySecurityRequestIdAndFieldAndActiveTrue(securityRequestId, field)
                .map(assignment -> assignment.isAssignedTo(username))
                .orElse(false);
    }

    /** 신청·분야에 활성 배정이 있는지(미배정 집계용). */
    @Transactional(readOnly = true)
    public boolean hasActiveAssignment(Long securityRequestId, ReviewField field) {
        return assignmentRepository
                .findBySecurityRequestIdAndFieldAndActiveTrue(securityRequestId, field)
                .isPresent();
    }

    /** 내게 배정된 활성 배정 목록(요청함 기본 보기). */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> listMine(MemberPrincipal principal) {
        return assignmentRepository
                .findByAssigneeUsernameAndActiveTrueOrderByAssignedAtDesc(principal.username())
                .stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    private Optional<String> preferredSibling(List<Long> siblingRequestIds, ReviewField field) {
        if (siblingRequestIds == null || siblingRequestIds.isEmpty()) {
            return Optional.empty();
        }
        return assignmentRepository.findBySecurityRequestIdInAndFieldAndActiveTrue(siblingRequestIds, field).stream()
                .map(Assignment::getAssigneeUsername)
                .findFirst();
    }
}
