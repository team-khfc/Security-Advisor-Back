package kr.securityadvisor.audit.controller;

import kr.securityadvisor.audit.dto.AuditListQuery;
import kr.securityadvisor.audit.dto.AuditListResponse;
import kr.securityadvisor.audit.service.AuditQueryService;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** F-14: 역할별 허용 범위 안에서 업무 이력을 검색하고 페이지로 조회한다. */
@RestController
@RequestMapping("/api/audit-events")
@RequiredArgsConstructor
public class AuditEventController {
    private final AuditQueryService auditQueryService;
    /** throughId를 유지하면 조회 이후 새 이력으로 인한 페이지 중복·누락을 막는다. */
    @GetMapping
    public AuditListResponse search(
            @CurrentMember MemberPrincipal member,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long throughId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditQueryService.search(
                member, new AuditListQuery(targetType, targetId, action, query, throughId, page, size));
    }
}
