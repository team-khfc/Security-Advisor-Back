package kr.securityadvisor.audit.dto;

import kr.securityadvisor.common.error.BadRequestException;

/** 이력 검색 조건. throughId는 페이지 이동 중 새 기록이 앞에 끼어들지 않도록 고정한다. */
public record AuditListQuery(
        String targetType, String targetId, String action, String query, Long throughId, int page, int size) {
    public AuditListQuery {
        query = query == null ? "" : query.strip();
        if (query.length() > 200 || page < 0 || size < 1 || size > 100 || (throughId != null && throughId < 0))
            throw new BadRequestException("이력 검색 조건이나 페이지 값이 올바르지 않습니다");
    }
}
