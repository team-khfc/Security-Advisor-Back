package kr.securityadvisor.request.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import kr.securityadvisor.assignment.dto.AssignmentResponse;
import kr.securityadvisor.assignment.dto.ReassignRequest;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.dto.AuditEventResponse;
import kr.securityadvisor.audit.service.AuditQueryService;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.DeadlineKind;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.DeadlineResponse;
import kr.securityadvisor.request.dto.SecurityRequestListItem;
import kr.securityadvisor.request.dto.SecurityRequestResponse;
import kr.securityadvisor.request.dto.WithdrawRequest;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청 유스케이스 중 신청자 행위(조회·철회)와 신청에 붙은 운영 정보(배정·기한·처리 이력) 조회·재배정.
 * 협의 요청·제출·확정처럼 review 컨텍스트의 사실이 필요한 전이는 review 의 애플리케이션 서비스가 수행한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SecurityRequestService {

    private final RequestAccessPolicy accessPolicy;
    private final AnswerService answerService;
    private final AssignmentService assignmentService;
    private final DeadlineService deadlineService;
    private final AuditRecorder auditRecorder;
    private final AuditQueryService auditQueryService;
    private final SecurityRequestRepository securityRequestRepository;
    private final BusinessCaseRepository businessCaseRepository;
    private final Clock clock;

    /**
     * 역할별 "내 신청" 목록(최근 수정순). 신청자: 자기 사업 건의 신청 / 담당자: 내게 활성 배정된 신청 / 관리자: 전체.
     * status·docType 은 선택 필터. 시연 규모에서 메모리 필터·페이지로 충분하다.
     *
     * @throws BadRequestException 상태 이름이 틀릴 때(400)
     */
    public PageResponse<SecurityRequestListItem> list(
            MemberPrincipal principal, String status, String docType, Pageable pageable) {
        RequestStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = RequestStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("status 값이 올바르지 않습니다: " + status);
            }
        }
        DocTypeCode docTypeFilter = null;
        if (docType != null && !docType.isBlank()) {
            try {
                docTypeFilter = DocTypeCode.of(docType);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("docType 값이 올바르지 않습니다: " + docType);
            }
        }
        List<SecurityRequest> visible = switch (principal.role()) {
            case APPLICANT ->
                businessCaseRepository.findByApplicantUsernameOrderByIdAsc(principal.username()).stream()
                        .flatMap(c -> securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(c.getId()).stream())
                        .toList();
            case SECURITY, INFRA ->
                assignmentService.listMine(principal).stream()
                        .map(AssignmentResponse::securityRequestId)
                        .distinct()
                        .map(id -> securityRequestRepository.findById(id).orElse(null))
                        .filter(Objects::nonNull)
                        .toList();
            case ADMIN -> securityRequestRepository.findAll();
        };
        final RequestStatus st = statusFilter;
        final DocTypeCode dt = docTypeFilter;
        Map<Long, BusinessCase> cases = new HashMap<>();
        List<SecurityRequestListItem> items = visible.stream()
                .filter(r -> st == null || r.getStatus() == st)
                .filter(r -> dt == null || dt.equals(r.getDocType()))
                .sorted(Comparator.comparing(SecurityRequest::getUpdatedAt).reversed())
                .map(r -> SecurityRequestListItem.from(
                        r,
                        cases.computeIfAbsent(
                                r.getBusinessCaseId(),
                                id -> businessCaseRepository.findById(id).orElseThrow())))
                .toList();
        int from = (int) Math.min(pageable.getOffset(), items.size());
        int to = Math.min(from + pageable.getPageSize(), items.size());
        return new PageResponse<>(
                items.subList(from, to), pageable.getPageNumber(), pageable.getPageSize(), items.size());
    }

    /** 신청 상세(서류별 답변 포함). */
    public SecurityRequestResponse get(MemberPrincipal principal, Long securityRequestId) {
        SecurityRequest request = accessPolicy.viewableRequest(principal, securityRequestId);
        return SecurityRequestResponse.from(request, answerService.requestAnswers(securityRequestId));
    }

    /**
     * 신청자 철회(F-05). 사유 필수, 이후 조회만 가능. 같은 사업 건의 다른 신청은 바뀌지 않는다(불변식 16).
     * 활성 기한은 종료하고(기록 보존) 감사 이력을 남긴다.
     */
    @Transactional
    public SecurityRequestResponse withdraw(
            MemberPrincipal principal, Long securityRequestId, WithdrawRequest request) {
        SecurityRequest securityRequest = accessPolicy.ownedRequest(principal, securityRequestId);
        securityRequest.requireRevision(request.expectedRevision());
        securityRequest.withdraw(request.reason());
        Instant now = clock.instant();
        for (DeadlineKind kind : DeadlineKind.values()) {
            deadlineService.endActive(principal, securityRequestId, kind, now, "철회");
        }
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(securityRequestId),
                securityRequest.getRevision(),
                AuditAction.REQUEST_WITHDRAWN,
                request.reason(),
                "신청자 철회 — " + securityRequest.getStage() + "단계에서 종료");
        return SecurityRequestResponse.from(securityRequest, answerService.requestAnswers(securityRequestId));
    }

    /** 신청의 배정 이력(활성·비활성). */
    public List<AssignmentResponse> assignments(MemberPrincipal principal, Long securityRequestId) {
        accessPolicy.viewableRequest(principal, securityRequestId);
        return assignmentService.listForRequest(securityRequestId);
    }

    /** 기관 관리자의 재배정(또는 미배정 분야 수동 배정). */
    @Transactional
    public AssignmentResponse reassign(MemberPrincipal admin, Long securityRequestId, ReassignRequest request) {
        SecurityRequest securityRequest = accessPolicy.viewableRequest(admin, securityRequestId);
        BusinessCase businessCase = accessPolicy.viewableCase(admin, securityRequest.getBusinessCaseId());
        return assignmentService.reassign(admin, securityRequestId, businessCase.getApplicantUsername(), request);
    }

    /** 신청의 기한 목록(오늘 기준 상태 포함). */
    public List<DeadlineResponse> deadlines(MemberPrincipal principal, Long securityRequestId) {
        SecurityRequest securityRequest = accessPolicy.viewableRequest(principal, securityRequestId);
        BusinessCase businessCase = accessPolicy.viewableCase(principal, securityRequest.getBusinessCaseId());
        return deadlineService.listForRequest(securityRequestId, businessCase.getPublicationId());
    }

    /** 신청의 처리 이력(감사 이력 중 이 신청을 대상으로 한 것). 신청자는 자기 신청만 본다. */
    public List<AuditEventResponse> auditTrail(MemberPrincipal principal, Long securityRequestId) {
        accessPolicy.viewableRequest(principal, securityRequestId);
        return auditQueryService.forTarget(AuditTargetType.SECURITY_REQUEST, String.valueOf(securityRequestId));
    }
}
