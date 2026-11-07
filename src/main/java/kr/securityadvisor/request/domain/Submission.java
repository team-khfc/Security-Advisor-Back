package kr.securityadvisor.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 제출본 — 제출 시점의 불변 스냅샷(공통 값 + 서류별 값 + 설정 버전). 생성 후 바뀌지 않는다(불변식 4).
 * setter 도, 수정 메서드도 두지 않는다. 보완은 새 제출본(sequenceNo 증가)으로 만든다.
 */
@Entity
@Table(name = "submissions")
public class Submission extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "submission_id")
    private Long id;

    @Column(name = "security_request_id", nullable = false)
    private Long securityRequestId;

    @Column(name = "publication_id", nullable = false)
    private Long publicationId;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String snapshot;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "submitted_by", nullable = false, length = 50)
    private String submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    /** JPA 전용 기본 생성자. */
    protected Submission() {}

    private Submission(
            Long securityRequestId,
            Long publicationId,
            int sequenceNo,
            String snapshot,
            String idempotencyKey,
            String submittedBy,
            Instant submittedAt) {
        this.securityRequestId = securityRequestId;
        this.publicationId = publicationId;
        this.sequenceNo = sequenceNo;
        this.snapshot = snapshot;
        this.idempotencyKey = idempotencyKey;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
    }

    public static Submission of(
            Long securityRequestId,
            Long publicationId,
            int sequenceNo,
            String snapshotJson,
            String idempotencyKey,
            String submittedBy,
            Instant submittedAt) {
        return new Submission(
                securityRequestId, publicationId, sequenceNo, snapshotJson, idempotencyKey, submittedBy, submittedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getSecurityRequestId() {
        return securityRequestId;
    }

    public Long getPublicationId() {
        return publicationId;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
