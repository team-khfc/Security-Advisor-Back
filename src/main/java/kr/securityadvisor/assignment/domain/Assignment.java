package kr.securityadvisor.assignment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ReviewField;

/**
 * 배정 — 한 신청의 한 분야를 맡은 담당자 1명(F-12). 재배정하면 이 행은 비활성으로 남고 새 행이 생긴다(불변식 20).
 * 신청자 본인은 담당자가 될 수 없다(불변식 21) — 호출자가 신청자 아이디를 넘겨 검사한다.
 */
@Entity
@Table(name = "assignments")
public class Assignment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long id;

    @Column(name = "security_request_id", nullable = false)
    private Long securityRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewField field;

    @Column(name = "assignee_username", nullable = false, length = 50)
    private String assigneeUsername;

    @Column(nullable = false)
    private boolean active;

    @Column(columnDefinition = "text")
    private String reason;

    /** null 이면 자동 배정. */
    @Column(name = "assigned_by", length = 50)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    /** JPA 전용 기본 생성자. */
    protected Assignment() {}

    private Assignment(
            Long securityRequestId,
            ReviewField field,
            String assigneeUsername,
            String reason,
            String assignedBy,
            Instant assignedAt) {
        this.securityRequestId = securityRequestId;
        this.field = field;
        this.assigneeUsername = assigneeUsername;
        this.reason = reason;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
        this.active = true;
    }

    /** 2단계 진입 시 자동 배정. */
    public static Assignment autoAssign(
            Long securityRequestId, ReviewField field, String assigneeUsername, String applicantUsername, Instant now) {
        requireNotApplicant(assigneeUsername, applicantUsername);
        return new Assignment(securityRequestId, field, assigneeUsername, null, null, now);
    }

    /** 기관 관리자의 재배정. 사유 필수. */
    public static Assignment reassign(
            Long securityRequestId,
            ReviewField field,
            String assigneeUsername,
            String applicantUsername,
            String adminUsername,
            String reason,
            Instant now) {
        requireNotApplicant(assigneeUsername, applicantUsername);
        if (reason == null || reason.isBlank()) {
            throw new DomainRuleViolationException("재배정에는 사유가 필요합니다");
        }
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new DomainRuleViolationException("재배정 주체(기관 관리자)가 필요합니다");
        }
        return new Assignment(securityRequestId, field, assigneeUsername, reason.strip(), adminUsername, now);
    }

    public Long getId() {
        return id;
    }

    public Long getSecurityRequestId() {
        return securityRequestId;
    }

    public ReviewField getField() {
        return field;
    }

    public String getAssigneeUsername() {
        return assigneeUsername;
    }

    public boolean isActive() {
        return active;
    }

    public String getReason() {
        return reason;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }

    public boolean isAssignedTo(String username) {
        return active && assigneeUsername.equals(username);
    }

    /** 재배정으로 이 배정을 내린다. 행은 지우지 않는다. */
    public void deactivate(Instant now) {
        if (!active) {
            throw new DomainRuleViolationException("이미 비활성인 배정입니다");
        }
        this.active = false;
        this.deactivatedAt = now;
    }

    private static void requireNotApplicant(String assigneeUsername, String applicantUsername) {
        if (assigneeUsername == null || assigneeUsername.isBlank()) {
            throw new DomainRuleViolationException("담당자 아이디가 필요합니다");
        }
        if (assigneeUsername.equals(applicantUsername)) {
            throw new DomainRuleViolationException("신청자 본인은 자기 신청의 담당자가 될 수 없습니다");
        }
    }
}
