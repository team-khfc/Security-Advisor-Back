package kr.securityadvisor.review.domain;

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
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ReviewField;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 보완 요청(F-05). 담당자가 대상 항목·사유를 명시해 신청자에게 추가 확인을 요청한다.
 * OPEN → ANSWERED(신청자 답변) → RESOLVED(담당자 재검토 후 협의 완료 시). 기존 값은 지우지 않는다.
 */
@Entity
@Table(name = "supplements")
public class Supplement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplement_id")
    private Long id;

    @Column(name = "security_request_id", nullable = false)
    private Long securityRequestId;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewField field;

    /** 대상 항목 키 목록 JSON(["network.source", ...]). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_field_keys", nullable = false)
    private String targetFieldKeys;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplementStatus status;

    @Column(name = "requested_by", nullable = false, length = 50)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "answer_note", columnDefinition = "text")
    private String answerNote;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "resolved_by", length = 50)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(nullable = false)
    private long revision;

    /** JPA 전용 기본 생성자. */
    protected Supplement() {}

    private Supplement(
            Long securityRequestId,
            Long reviewId,
            ReviewField field,
            String targetFieldKeysJson,
            String reason,
            String requestedBy,
            Instant requestedAt) {
        this.securityRequestId = securityRequestId;
        this.reviewId = reviewId;
        this.field = field;
        this.targetFieldKeys = targetFieldKeysJson;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.status = SupplementStatus.OPEN;
    }

    /**
     * 담당자의 보완 요청. 사유 필수.
     *
     * @param targetFieldKeysJson 대상 항목 키 목록 JSON(비어 있으면 안 됨)
     */
    public static Supplement request(
            Long securityRequestId,
            Long reviewId,
            ReviewField field,
            String targetFieldKeysJson,
            boolean hasTargets,
            String reason,
            String requestedBy,
            Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new DomainRuleViolationException("보완 요청에는 사유가 필요합니다");
        }
        if (!hasTargets) {
            throw new DomainRuleViolationException("보완 요청에는 대상 항목이 하나 이상 필요합니다");
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new DomainRuleViolationException("보완 요청 담당자가 필요합니다");
        }
        return new Supplement(
                securityRequestId, reviewId, field, targetFieldKeysJson, reason.strip(), requestedBy, now);
    }

    public Long getId() {
        return id;
    }

    public Long getSecurityRequestId() {
        return securityRequestId;
    }

    public Long getReviewId() {
        return reviewId;
    }

    public ReviewField getField() {
        return field;
    }

    public String getTargetFieldKeys() {
        return targetFieldKeys;
    }

    public String getReason() {
        return reason;
    }

    public SupplementStatus getStatus() {
        return status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public String getAnswerNote() {
        return answerNote;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public long getRevision() {
        return revision;
    }

    public boolean isOpen() {
        return status == SupplementStatus.OPEN;
    }

    /**
     * @throws ConflictException expectedRevision 이 현재 revision 과 다를 때(409)
     */
    public void requireRevision(long expectedRevision) {
        if (expectedRevision != revision) {
            throw new ConflictException("보완 요청이 다른 곳에서 수정되었습니다. 최신 상태(revision " + revision + ")를 다시 확인하세요");
        }
    }

    /** 신청자가 답했다(항목 값은 답변 API 로 따로 저장, 여기는 메모). */
    public void answer(String note, Instant now) {
        if (status != SupplementStatus.OPEN) {
            throw new DomainRuleViolationException("미해결 상태의 보완 요청에만 답할 수 있습니다");
        }
        this.answerNote = note == null ? null : note.strip();
        this.answeredAt = now;
        this.status = SupplementStatus.ANSWERED;
    }

    /** 담당자 재검토 후 해소(협의 완료 시 일괄). */
    public void resolve(String staffUsername, Instant now) {
        if (status != SupplementStatus.ANSWERED) {
            throw new DomainRuleViolationException("답변된 보완 요청만 해소할 수 있습니다");
        }
        this.resolvedBy = staffUsername;
        this.resolvedAt = now;
        this.status = SupplementStatus.RESOLVED;
    }
}
