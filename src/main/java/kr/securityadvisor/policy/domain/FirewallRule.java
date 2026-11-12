package kr.securityadvisor.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;

/**
 * 방화벽 규칙 한 줄(F-15). 값은 확정 제출본에서 복사되어 바뀌지 않는다(불변식 26). 전이는 사람(INFRA)의 기록으로만(불변식 27).
 * 종료일이 없으면 정기 재검토일이 있어야 한다(불변식 28). 적용 완료 ≠ 서류 확정.
 */
@Entity
@Table(name = "firewall_rules")
public class FirewallRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "firewall_rule_id")
    private Long id;

    @Column(name = "security_request_id", nullable = false)
    private Long securityRequestId;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "rule_no", nullable = false)
    private int ruleNo;

    @Column(nullable = false, length = 200)
    private String source;

    @Column(nullable = false, length = 200)
    private String destination;

    @Column(nullable = false, length = 20)
    private String protocol;

    @Column(nullable = false, length = 50)
    private String port;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FirewallRuleStatus status;

    @Column(name = "applied_by", length = 50)
    private String appliedBy;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "application_note", columnDefinition = "text")
    private String applicationNote;

    @Column(columnDefinition = "text")
    private String reason;

    @Version
    @Column(nullable = false)
    private long revision;

    /** 이 규칙을 연장하려고 만든 신청(F-15). 규칙 값 자체는 바뀌지 않는다. */
    @Column(name = "renewal_request_id")
    private Long renewalRequestId;

    @Column(name = "review_months", nullable = false)
    private int reviewMonths = 12;

    @Column(name = "review_notice_days", nullable = false)
    private int reviewNoticeDays = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_decision", length = 30)
    private PeriodicReviewDecision reviewDecision;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "review_history", nullable = false, columnDefinition = "jsonb")
    private java.util.List<PeriodicReviewRecord> reviewHistory = new java.util.ArrayList<>();

    protected FirewallRule() {}

    /** 생성 시 게시본 정책을 복사한다. 이미 존재하는 규칙의 날짜와 주기는 바꾸지 않는다. */
    public void initializeReviewPolicy(kr.securityadvisor.common.schema.FirewallPolicy policy) {
        if (id != null) throw new DomainRuleViolationException("기존 규칙의 주기는 변경할 수 없습니다");
        reviewMonths = policy.reviewMonths();
        reviewNoticeDays = policy.reviewNoticeDays();
        if (endDate == null) reviewDate = startDate.plusMonths(reviewMonths);
    }

    /** 현재 적용 중인 상시 규칙의 판단을 기록하며 실제 장비·제출본은 변경하지 않는다. */
    public void review(PeriodicReviewDecision decision, String reason, String actor, Instant now, LocalDate today) {
        requireActor(actor);
        requireReason(reason, "정기 재검토");
        if (reason.length() > 2000 || decision == null)
            throw new DomainRuleViolationException("재검토 결과와 2000자 이내 사유가 필요합니다");
        if (status != FirewallRuleStatus.APPLIED || endDate != null)
            throw new DomainRuleViolationException("적용 중인 상시 규칙만 정기 재검토할 수 있습니다");
        LocalDate previous = reviewDate;
        if (decision == PeriodicReviewDecision.KEEP) reviewDate = today.plusMonths(reviewMonths);
        reviewDecision = decision;
        var updated = new java.util.ArrayList<>(reviewHistory);
        updated.add(new PeriodicReviewRecord(decision, reason.strip(), actor, now, previous, reviewDate));
        reviewHistory = updated;
    }

    /** 조회·알림·처리할 일의 공통 날짜 판정. */
    public String reviewStatus(LocalDate today) {
        if (status != FirewallRuleStatus.APPLIED || endDate != null) return "NOT_REQUIRED";
        if (reviewDecision == PeriodicReviewDecision.CHANGE_REQUIRED
                || reviewDecision == PeriodicReviewDecision.REVOKE_REQUIRED) return reviewDecision.name();
        if (reviewDate.isBefore(today)) return "OVERDUE";
        if (!reviewDate.isAfter(today.plusDays(reviewNoticeDays))) return "DUE";
        return "SCHEDULED";
    }

    public boolean needsReview(LocalDate today) {
        return java.util.Set.of("DUE", "OVERDUE", "CHANGE_REQUIRED", "REVOKE_REQUIRED")
                .contains(reviewStatus(today));
    }

    public int getReviewMonths() {
        return reviewMonths;
    }

    public int getReviewNoticeDays() {
        return reviewNoticeDays;
    }

    public java.util.List<PeriodicReviewRecord> getReviewHistory() {
        return java.util.List.copyOf(reviewHistory);
    }

    private FirewallRule(
            Long securityRequestId,
            Long submissionId,
            int ruleNo,
            String source,
            String destination,
            String protocol,
            String port,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate reviewDate) {
        this.securityRequestId = securityRequestId;
        this.submissionId = submissionId;
        this.ruleNo = ruleNo;
        this.source = source;
        this.destination = destination;
        this.protocol = protocol;
        this.port = port;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reviewDate = reviewDate;
        this.status = FirewallRuleStatus.PENDING_APPLY;
    }

    /** 확정 제출본 값으로 생성. 종료일이 없으면 재검토일 필수. */
    public static FirewallRule fromConfirmedSubmission(
            Long securityRequestId,
            Long submissionId,
            int ruleNo,
            String source,
            String destination,
            String protocol,
            String port,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate reviewDate) {
        requireText(source, "출발지");
        requireText(destination, "목적지");
        requireText(protocol, "프로토콜");
        requireText(port, "포트");
        if (startDate == null) {
            throw new DomainRuleViolationException("규칙 시작일이 필요합니다");
        }
        if (endDate == null && reviewDate == null) {
            throw new DomainRuleViolationException("종료일 없는 규칙은 정기 재검토일이 필요합니다(불변식 28)");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new DomainRuleViolationException("종료일이 시작일보다 앞섭니다");
        }
        return new FirewallRule(
                securityRequestId,
                submissionId,
                ruleNo,
                source.strip(),
                destination.strip(),
                protocol.strip(),
                port.strip(),
                startDate,
                endDate,
                reviewDate);
    }

    public void requireRevision(long expectedRevision) {
        if (expectedRevision != revision) {
            throw new ConflictException("규칙이 다른 곳에서 수정되었습니다(revision " + revision + ")");
        }
    }

    /** INFRA 가 장비 적용을 완료했다고 기록한다(시스템 자동 적용 없음). */
    public void recordApplied(String infraUsername, String note, Instant now) {
        requireActor(infraUsername);
        if (status != FirewallRuleStatus.PENDING_APPLY) {
            throw new DomainRuleViolationException("적용 대기 상태의 규칙만 적용 완료로 기록할 수 있습니다 (현재 " + status.label() + ")");
        }
        this.status = FirewallRuleStatus.APPLIED;
        this.appliedBy = infraUsername;
        this.appliedAt = now;
        this.applicationNote = note == null ? null : note.strip();
    }

    /** 적용 불가 기록 — 사유 필수. */
    public void recordNotApplicable(String infraUsername, String reason, Instant now) {
        requireActor(infraUsername);
        requireReason(reason, "적용 불가");
        if (status != FirewallRuleStatus.PENDING_APPLY) {
            throw new DomainRuleViolationException("적용 대기 상태의 규칙만 적용 불가로 기록할 수 있습니다 (현재 " + status.label() + ")");
        }
        this.status = FirewallRuleStatus.NOT_APPLICABLE;
        this.appliedBy = infraUsername;
        this.appliedAt = now;
        this.reason = reason.strip();
    }

    /** 활성(적용 완료) 규칙의 회수 — 사유 필수. */
    public void revoke(String infraUsername, String reason, Instant now) {
        requireActor(infraUsername);
        requireReason(reason, "회수");
        if (status != FirewallRuleStatus.APPLIED) {
            throw new DomainRuleViolationException("적용 완료된 규칙만 회수할 수 있습니다 (현재 " + status.label() + ")");
        }
        this.status = FirewallRuleStatus.REVOKED;
        this.reason = reason.strip();
        this.appliedBy = infraUsername;
        this.appliedAt = now;
    }

    /** 연장 가능한지: 적용 완료(만료 예정·만료 포함) 규칙만, 진행 중인 연장 신청이 없을 때만. */
    public void requireRenewable(boolean previousRenewalStillOpen) {
        if (status != FirewallRuleStatus.APPLIED) {
            throw new DomainRuleViolationException("적용 완료된 규칙만 연장할 수 있습니다(현재 " + status.label() + ")");
        }
        if (renewalRequestId != null && previousRenewalStillOpen) {
            throw new DomainRuleViolationException("진행 중인 연장 신청(" + renewalRequestId + ")이 이미 있습니다");
        }
    }

    /** 연장 신청을 연결한다({@link #requireRenewable} 뒤에). 규칙 값은 바뀌지 않는다. */
    public void linkRenewal(Long requestId) {
        if (requestId == null) {
            throw new IllegalArgumentException("연장 신청 ID 가 필요합니다");
        }
        this.renewalRequestId = requestId;
    }

    public Long getRenewalRequestId() {
        return renewalRequestId;
    }

    private static void requireActor(String username) {
        if (username == null || username.isBlank()) {
            throw new DomainRuleViolationException("기록자(INFRA 담당자) 없이는 규칙 상태를 바꿀 수 없습니다");
        }
    }

    private static void requireReason(String reason, String what) {
        if (reason == null || reason.isBlank()) {
            throw new DomainRuleViolationException(what + "에는 사유가 필요합니다");
        }
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException("규칙의 " + what + " 값이 없습니다");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSecurityRequestId() {
        return securityRequestId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public int getRuleNo() {
        return ruleNo;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getPort() {
        return port;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public FirewallRuleStatus getStatus() {
        return status;
    }

    public String getAppliedBy() {
        return appliedBy;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public String getApplicationNote() {
        return applicationNote;
    }

    public String getReason() {
        return reason;
    }

    public long getRevision() {
        return revision;
    }
}
