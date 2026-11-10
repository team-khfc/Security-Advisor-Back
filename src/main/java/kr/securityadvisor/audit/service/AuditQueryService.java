package kr.securityadvisor.audit.service;

import java.util.List;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.dto.AuditEventResponse;
import kr.securityadvisor.audit.dto.AuditListQuery;
import kr.securityadvisor.audit.dto.AuditListResponse;
import kr.securityadvisor.audit.repository.AuditListingRepository;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 전체·담당자 배정 신청·신청자 본인 업무 이력의 조회 경계. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuditQueryService {
    private final AuditListingRepository listingRepository;
    /** 기존 신청별 배열 계약에도 동일한 권한 범위를 적용한다. */
    public List<AuditEventResponse> forTarget(MemberPrincipal member, AuditTargetType type, String id) {
        return listingRepository.forTarget(member, type.name(), id);
    }
    /** 권한 필터가 검색·전체 건수·페이지에 동일하게 적용된다. */
    public AuditListResponse search(MemberPrincipal member, AuditListQuery filter) {
        try {
            if (filter.targetType() != null) AuditTargetType.valueOf(filter.targetType());
            if (filter.action() != null) AuditAction.valueOf(filter.action());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("이력 대상이나 업무 구분이 올바르지 않습니다");
        }
        return listingRepository.search(member, filter);
    }
}
