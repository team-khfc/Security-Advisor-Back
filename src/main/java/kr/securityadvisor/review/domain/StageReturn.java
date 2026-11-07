package kr.securityadvisor.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 차수 반환 기록(불변식 11). 전이 자체는 {@code SecurityRequest.returnToStage} 가 하고, 이 엔티티는 사유·지정 항목을 보존한다.
 * 생성 후 수정하지 않는다.
 */
@Entity
@Table(name = "stage_returns")
public class StageReturn extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_return_id")
    private Long id;

    @Column(name = "security_request_id", nullable = false)
    private Long securityRequestId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "from_stage", nullable = false)
    private int fromStage;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "to_stage", nullable = false)
    private int toStage;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    /** "보완 필요/재작성 필요"로 지정한 항목 키 JSON 목록. 화면은 이 항목을 강조 표시한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_field_keys", nullable = false)
    private String targetFieldKeys;

    @Column(name = "returned_by", nullable = false, length = 50)
    private String returnedBy;

    @Column(name = "returned_at", nullable = false)
    private Instant returnedAt;

    /** JPA 전용 기본 생성자. */
    protected StageReturn() {}

    private StageReturn(
            Long securityRequestId,
            int fromStage,
            int toStage,
            String reason,
            String targetFieldKeysJson,
            String returnedBy,
            Instant returnedAt) {
        this.securityRequestId = securityRequestId;
        this.fromStage = fromStage;
        this.toStage = toStage;
        this.reason = reason;
        this.targetFieldKeys = targetFieldKeysJson;
        this.returnedBy = returnedBy;
        this.returnedAt = returnedAt;
    }

    public static StageReturn record(
            Long securityRequestId,
            int fromStage,
            int toStage,
            String reason,
            String targetFieldKeysJson,
            String returnedBy,
            Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new DomainRuleViolationException("차수 반환에는 사유가 필요합니다");
        }
        return new StageReturn(
                securityRequestId, fromStage, toStage, reason.strip(), targetFieldKeysJson, returnedBy, now);
    }

    public Long getId() {
        return id;
    }

    public Long getSecurityRequestId() {
        return securityRequestId;
    }

    public int getFromStage() {
        return fromStage;
    }

    public int getToStage() {
        return toStage;
    }

    public String getReason() {
        return reason;
    }

    public String getTargetFieldKeys() {
        return targetFieldKeys;
    }

    public String getReturnedBy() {
        return returnedBy;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }
}
