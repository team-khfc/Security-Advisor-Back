package kr.securityadvisor.audit.dto;

import java.util.List;

/** 같은 조회 기준 시점에서 이력의 건수와 페이지를 반환한다. */
public record AuditListResponse(
        List<AuditEventResponse> content, int page, int size, long totalElements, long throughId) {}
