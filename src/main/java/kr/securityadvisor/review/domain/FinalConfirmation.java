package kr.securityadvisor.review.domain;

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
 * 제출본 최종 확인(4단계, 분야별 1건 — 불변식 34). 생성 후 바뀌지 않는다. 확정은 신청 상태(CONFIRMED)로 표현한다.
 * 순서: INFRA 선행 → SECURITY(1차 범위 고정). 확인 ≠ 확정 ≠ 방화벽 적용.
 */
@Entity
@Table(name = "final_confirmations")
public class FinalConfirmation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "final_confirmation_id")
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "security_request_id", nullable = false)
    private Long securityRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewField field;

    @Column(columnDefinition = "text")
    private String opinion;

    @Column(name = "confirmed_by", nullable = false, length = 50)
    private String confirmedBy;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    protected FinalConfirmation() {}

    private FinalConfirmation(
            Long submissionId,
            Long securityRequestId,
            ReviewField field,
            String opinion,
            String confirmedBy,
            Instant confirmedAt) {
        this.submissionId = submissionId;
        this.securityRequestId = securityRequestId;
        this.field = field;
        this.opinion = opinion;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
    }

    /**
     * 분야 최종 확인. SECURITY 는 같은 제출본의 INFRA 확인이 먼저 있어야 한다(INFRA 선행).
     *
     * @param infraConfirmed 같은 제출본에 INFRA 최종 확인이 이미 있는지
     * @param alreadyConfirmed 같은 제출본·같은 분야 확인이 이미 있는지
     */
    public static FinalConfirmation confirm(
            Long submissionId,
            Long securityRequestId,
            ReviewField field,
            String opinion,
            String confirmedBy,
            boolean infraConfirmed,
            boolean alreadyConfirmed,
            Instant now) {
        if (confirmedBy == null || confirmedBy.isBlank()) {
            throw new DomainRuleViolationException("확인자 없이는 최종 확인할 수 없습니다");
        }
        if (alreadyConfirmed) {
            throw new DomainRuleViolationException(field + " 분야는 이 제출본을 이미 최종 확인했습니다");
        }
        if (field == ReviewField.SECURITY && !infraConfirmed) {
            throw new DomainRuleViolationException("INFRA 최종 확인 전에는 SECURITY 최종 확인을 할 수 없습니다(INFRA 선행)");
        }
        return new FinalConfirmation(
                submissionId, securityRequestId, field, opinion == null ? null : opinion.strip(), confirmedBy, now);
    }

    public Long getId() {
        return id;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public Long getSecurityRequestId() {
        return securityRequestId;
    }

    public ReviewField getField() {
        return field;
    }

    public String getOpinion() {
        return opinion;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
