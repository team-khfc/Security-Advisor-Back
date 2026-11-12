package kr.securityadvisor.llm.domain;

import jakarta.persistence.*;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.ConflictException;

/** 검사 원본은 불변으로 보존하고 담당자의 처리 기록만 개정한다. */
@Entity
@Table(name = "consistency_reviews")
public class ConsistencyReview extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consistency_review_id")
    private Long id;

    @Column(name = "security_request_id", nullable = false)
    private Long securityRequestId;

    @Column(nullable = false, columnDefinition = "text")
    private String result;

    @Column(nullable = false, columnDefinition = "text")
    private String decisions = "[]";

    @Version
    private long revision;

    protected ConsistencyReview() {}

    public static ConsistencyReview create(Long requestId, String result) {
        var review = new ConsistencyReview();
        review.securityRequestId = requestId;
        review.result = result;
        return review;
    }

    public void decide(long expected, String decisions) {
        if (revision != expected) throw new ConflictException("처리 기록이 변경됐습니다. 다시 조회해 주세요.");
        this.decisions = decisions;
    }

    public Long getId() {
        return id;
    }

    public Long getSecurityRequestId() {
        return securityRequestId;
    }

    public String getResult() {
        return result;
    }

    public String getDecisions() {
        return decisions;
    }

    public long getRevision() {
        return revision;
    }
}
