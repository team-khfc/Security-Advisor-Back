package kr.securityadvisor.request.service;

import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.common.error.ForbiddenException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 사업 건·신청 접근 통제(서버가 최종 판단, 불변 원칙 5). 신청자는 자기 것만 — 남의 자원은 존재 여부를 드러내지 않고 404.
 * 담당자는 자기 역할의 활성 배정 신청만, 관리자는 전체를 조회한다(ADR 0010).
 */
@Component
@RequiredArgsConstructor
public class RequestAccessPolicy {

    private final BusinessCaseRepository businessCaseRepository;
    private final SecurityRequestRepository securityRequestRepository;
    private final AssignmentService assignmentService;

    /**
     * 조회 가능한 사업 건을 읽는다.
     *
     * @throws NotFoundException 없거나, 신청자가 남의 사업 건을 볼 때
     */
    public BusinessCase viewableCase(MemberPrincipal principal, Long businessCaseId) {
        BusinessCase businessCase = businessCaseRepository
                .findById(businessCaseId)
                .orElseThrow(() -> new NotFoundException("사업 건을 찾을 수 없습니다: " + businessCaseId));
        boolean allowed = switch (principal.role()) {
            case ADMIN -> true;
            case APPLICANT -> businessCase.isOwnedBy(principal.username());
            case INFRA, SECURITY ->
                securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCaseId).stream()
                        .anyMatch(request -> canViewRequest(principal, request));
        };
        if (!allowed) {
            throw new NotFoundException("사업 건을 찾을 수 없습니다: " + businessCaseId);
        }
        return businessCase;
    }

    /**
     * 신청자 본인만 고칠 수 있는 사업 건을 읽는다(작성·보완 답변·철회).
     *
     * @throws ForbiddenException 담당자·관리자가 신청자 행위를 시도할 때
     */
    public BusinessCase ownedCase(MemberPrincipal principal, Long businessCaseId) {
        principal.requireAnyRole(MemberRole.APPLICANT);
        BusinessCase businessCase = viewableCase(principal, businessCaseId);
        if (!businessCase.isOwnedBy(principal.username())) {
            throw new ForbiddenException("신청자 본인만 할 수 있는 작업입니다");
        }
        return businessCase;
    }

    /**
     * 조회 가능한 신청을 읽는다. 같은 사업의 다른 신청에 배정되었다고 이 신청을 열 수는 없다.
     *
     * @throws NotFoundException 없거나 신청자가 남의 신청을 볼 때
     */
    public SecurityRequest viewableRequest(MemberPrincipal principal, Long securityRequestId) {
        SecurityRequest request = securityRequestRepository
                .findById(securityRequestId)
                .orElseThrow(() -> new NotFoundException("신청을 찾을 수 없습니다: " + securityRequestId));
        if (!canViewRequest(principal, request)) {
            throw new NotFoundException("신청을 찾을 수 없습니다: " + securityRequestId);
        }
        return request;
    }

    /** 목록·관련 신청·문서함도 상세와 같은 소유·역할·활성 배정 기준을 적용한다. */
    public boolean canViewRequest(MemberPrincipal principal, SecurityRequest request) {
        return switch (principal.role()) {
            case ADMIN -> true;
            case APPLICANT ->
                businessCaseRepository
                        .findById(request.getBusinessCaseId())
                        .map(businessCase -> businessCase.isOwnedBy(principal.username()))
                        .orElse(false);
            case INFRA, SECURITY ->
                assignmentService.isActiveAssignee(
                        request.getId(), ReviewField.valueOf(principal.role().name()), principal.username());
        };
    }

    /** 다른 컨텍스트의 목록은 엔티티 없이 신청 식별자로 권한을 확인한다. */
    public boolean canViewRequest(MemberPrincipal principal, Long requestId) {
        return securityRequestRepository
                .findById(requestId)
                .map(request -> canViewRequest(principal, request))
                .orElse(false);
    }

    /** 신청자 본인만 고칠 수 있는 신청을 읽는다. */
    public SecurityRequest ownedRequest(MemberPrincipal principal, Long securityRequestId) {
        SecurityRequest request = viewableRequest(principal, securityRequestId);
        ownedCase(principal, request.getBusinessCaseId());
        return request;
    }
}
