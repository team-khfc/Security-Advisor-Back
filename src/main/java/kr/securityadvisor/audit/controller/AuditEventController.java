package kr.securityadvisor.audit.controller;

import lombok.RequiredArgsConstructor;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.dto.AuditEventResponse;
import kr.securityadvisor.audit.service.AuditQueryService;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 감사 이력 API(F-14). 조회만 있고 수정·삭제 API 는 없다. */
@RestController
@RequestMapping("/api/audit-events")
@RequiredArgsConstructor
public class AuditEventController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditQueryService auditQueryService;

    /** 기관 관리자 전체 조회. targetType·targetId 로 좁힐 수 있다. 관리자가 아니면 403. */
    @GetMapping
    public PageResponse<AuditEventResponse> search(
            @CurrentMember MemberPrincipal member,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("occurredAt").descending());
        return auditQueryService.search(member, targetType, targetId, pageable);
    }
}
