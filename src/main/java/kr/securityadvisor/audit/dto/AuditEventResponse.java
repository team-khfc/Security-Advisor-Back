package kr.securityadvisor.audit.dto;

import java.time.Instant;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditEvent;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.common.security.MemberRole;

/** 감사 이력 응답(신청 상세의 "처리 이력" 탭, 관리자 조회). */
public record AuditEventResponse(
        Long auditEventId,
        Instant occurredAt,
        String actorUsername,
        MemberRole actorRole,
        AuditTargetType targetType,
        String targetId,
        Long targetRevision,
        AuditAction action,
        String reason,
        String summary,
        String requestId) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getOccurredAt(),
                event.getActorUsername(),
                event.getActorRole(),
                event.getTargetType(),
                event.getTargetId(),
                event.getTargetRevision(),
                event.getAction(),
                event.getReason(),
                event.getSummary(),
                event.getRequestId());
    }
}
