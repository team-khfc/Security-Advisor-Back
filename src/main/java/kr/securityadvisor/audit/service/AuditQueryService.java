package kr.securityadvisor.audit.service;

import java.util.List;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.dto.AuditEventResponse;
import kr.securityadvisor.audit.repository.AuditEventRepository;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 감사 이력 조회. 권한별 범위(3-3장): 관리자 전체, 그 외는 대상별 조회를 호출 컨텍스트가 접근 검사 후 요청한다. */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private final AuditEventRepository auditEventRepository;

    public AuditQueryService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /** 특정 대상의 이력(시간순). 호출자가 대상 접근 권한을 먼저 검사한다. */
    public List<AuditEventResponse> forTarget(AuditTargetType targetType, String targetId) {
        return auditEventRepository.findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc(targetType, targetId).stream()
                .map(AuditEventResponse::from)
                .toList();
    }

    /** 기관 관리자 전용 전체 조회(대상 필터 선택). */
    public PageResponse<AuditEventResponse> search(
            MemberPrincipal principal, AuditTargetType targetType, String targetId, Pageable pageable) {
        principal.requireAnyRole(MemberRole.ADMIN);
        if (targetType != null && targetId != null) {
            return PageResponse.from(
                    auditEventRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable),
                    AuditEventResponse::from);
        }
        return PageResponse.from(auditEventRepository.findAllByOrderByOccurredAtDescIdDesc(pageable), AuditEventResponse::from);
    }
}
