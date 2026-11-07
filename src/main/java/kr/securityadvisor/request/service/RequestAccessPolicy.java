package kr.securityadvisor.request.service;

import kr.securityadvisor.common.error.ForbiddenException;
import kr.securityadvisor.common.error.NotFoundException;
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
 * 담당자·관리자는 조회 가능하며, 검토 행위의 배정 검사는 review 컨텍스트가 한다.
 */
@Component
@RequiredArgsConstructor
public class RequestAccessPolicy {

    private final BusinessCaseRepository businessCaseRepository;
    private final SecurityRequestRepository securityRequestRepository;

    /**
     * 조회 가능한 사업 건을 읽는다.
     *
     * @throws NotFoundException 없거나, 신청자가 남의 사업 건을 볼 때
     */
    public BusinessCase viewableCase(MemberPrincipal principal, Long businessCaseId) {
        BusinessCase businessCase = businessCaseRepository
                .findById(businessCaseId)
                .orElseThrow(() -> new NotFoundException("사업 건을 찾을 수 없습니다: " + businessCaseId));
        if (principal.hasRole(MemberRole.APPLICANT) && !businessCase.isOwnedBy(principal.username())) {
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
        BusinessCase businessCase = viewableCase(principal, businessCaseId);
        if (!businessCase.isOwnedBy(principal.username())) {
            throw new ForbiddenException("신청자 본인만 할 수 있는 작업입니다");
        }
        return businessCase;
    }

    /**
     * 조회 가능한 신청을 읽는다(사업 건 접근 규칙과 같다).
     *
     * @throws NotFoundException 없거나 신청자가 남의 신청을 볼 때
     */
    public SecurityRequest viewableRequest(MemberPrincipal principal, Long securityRequestId) {
        SecurityRequest request = securityRequestRepository
                .findById(securityRequestId)
                .orElseThrow(() -> new NotFoundException("신청을 찾을 수 없습니다: " + securityRequestId));
        try {
            viewableCase(principal, request.getBusinessCaseId());
        } catch (NotFoundException e) {
            throw new NotFoundException("신청을 찾을 수 없습니다: " + securityRequestId);
        }
        return request;
    }

    /** 신청자 본인만 고칠 수 있는 신청을 읽는다. */
    public SecurityRequest ownedRequest(MemberPrincipal principal, Long securityRequestId) {
        SecurityRequest request = viewableRequest(principal, securityRequestId);
        ownedCase(principal, request.getBusinessCaseId());
        return request;
    }
}
