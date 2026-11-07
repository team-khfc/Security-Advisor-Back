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
import kr.securityadvisor.common.entity.BaseAuditEntity;
import kr.securityadvisor.common.schema.ReviewField;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 분야별 협의(F-04). 2단계 진입 시 서류 유형의 협의 분야마다 하나씩 만들어진다. 신청은 ID 로만 참조한다.
 * 할당·검토·보완·완료 전이는 다음 단계에서 도메인 메서드로 추가한다(이 단계에서는 생성만).
 */
@Entity
@Table(name = "reviews")
public class Review extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    @Column(name = "security_request_id", nullable = false)
    private Long securityRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewField field;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String checklist;

    @Column(columnDefinition = "text")
    private String opinion;

    @Column(name = "completed_by", length = 50)
    private String completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private long revision;

    /** JPA 전용 기본 생성자. */
    protected Review() {}

    private Review(Long securityRequestId, ReviewField field) {
        this.securityRequestId = securityRequestId;
        this.field = field;
        this.status = ReviewStatus.WAITING;
    }

    /** 협의 요청(1→2단계) 시 분야별로 만든다. 검토 대기로 시작한다. */
    public static Review open(Long securityRequestId, ReviewField field) {
        return new Review(securityRequestId, field);
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

    public ReviewStatus getStatus() {
        return status;
    }

    public String getChecklist() {
        return checklist;
    }

    public String getOpinion() {
        return opinion;
    }

    public String getCompletedBy() {
        return completedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public long getRevision() {
        return revision;
    }

    public boolean isCompleted() {
        return status == ReviewStatus.COMPLETED;
    }
}
