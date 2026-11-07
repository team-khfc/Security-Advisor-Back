package kr.securityadvisor.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import kr.securityadvisor.common.security.MemberRole;

/**
 * 감사 이력 한 건 — 누가·언제·무엇을·어느 버전에서·왜 했는지(F-14). 추가 전용이며 수정 메서드가 없다(불변식 24).
 * 비밀값·비공개 컨텍스트 원문·AI 프롬프트 원문은 담지 않는다(불변식 25) — summary 는 요약·참조 ID 만.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_event_id")
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_username", length = 50, updatable = false)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 20, updatable = false)
    private MemberRole actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50, updatable = false)
    private AuditTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 100, updatable = false)
    private String targetId;

    @Column(name = "target_revision", updatable = false)
    private Long targetRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private AuditAction action;

    @Column(columnDefinition = "text", updatable = false)
    private String reason;

    @Column(columnDefinition = "text", updatable = false)
    private String summary;

    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    /** JPA 전용 기본 생성자. */
    protected AuditEvent() {}

    private AuditEvent(
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
        this.occurredAt = occurredAt;
        this.actorUsername = actorUsername;
        this.actorRole = actorRole;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetRevision = targetRevision;
        this.action = action;
        this.reason = reason;
        this.summary = summary;
        this.requestId = requestId;
    }

    public static AuditEvent of(
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
        if (targetType == null || targetId == null || action == null || occurredAt == null) {
            throw new IllegalArgumentException("감사 이력에는 시각·대상·행위가 필요합니다");
        }
        return new AuditEvent(
                occurredAt, actorUsername, actorRole, targetType, targetId, targetRevision, action, reason, summary, requestId);
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public MemberRole getActorRole() {
        return actorRole;
    }

    public AuditTargetType getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public Long getTargetRevision() {
        return targetRevision;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public String getSummary() {
        return summary;
    }

    public String getRequestId() {
        return requestId;
    }
}
