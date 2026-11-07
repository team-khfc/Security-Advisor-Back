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
import java.util.Map;
import kr.securityadvisor.common.entity.BaseAuditEntity;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ReviewField;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 분야별 협의(F-04) 애그리거트. 2단계 진입 시 서류 유형의 협의 분야마다 하나씩 만들어지고 신청은 ID 로만 참조한다.
 *
 * <p>할당 완료와 협의 완료는 별도 상태다(불변식 12): INFRA 는 할당만 끝내고 검토를 보류할 수 있다.
 * SECURITY 협의 완료는 INFRA 할당 완료 이후에만 가능하다(불변식 9 — 1차 범위 INFRA 선행 고정).
 * 협의 완료는 체크리스트 전부 확인 + 의견 필수.
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

    /** 체크 결과 JSON({"source": true, ...}). 직렬화는 service 가 한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String checklist;

    @Column(columnDefinition = "text")
    private String opinion;

    @Column(name = "allocated_by", length = 50)
    private String allocatedBy;

    @Column(name = "allocated_at")
    private Instant allocatedAt;

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

    public String getAllocatedBy() {
        return allocatedBy;
    }

    public Instant getAllocatedAt() {
        return allocatedAt;
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

    /** INFRA 가 서버·IP·포트 할당을 끝냈는지(협의 완료와 무관). */
    public boolean isAllocated() {
        return allocatedAt != null;
    }

    /**
     * @throws ConflictException expectedRevision 이 현재 revision 과 다를 때(409)
     */
    public void requireRevision(long expectedRevision) {
        if (expectedRevision != revision) {
            throw new ConflictException("협의가 다른 곳에서 수정되었습니다. 최신 상태(revision " + revision + ")를 다시 확인하세요");
        }
    }

    /** 담당자가 값을 입력·확인하기 시작하면 검토 중으로 둔다(대기·할당 완료·재검토에서). 이미 진행 중이면 그대로. */
    public void touch() {
        if (status == ReviewStatus.WAITING || status == ReviewStatus.RECHECK) {
            this.status = ReviewStatus.IN_PROGRESS;
        }
    }

    /**
     * INFRA 할당 완료(불변식 12 — 협의 완료가 아니다). 검토는 계속 보류할 수 있다.
     *
     * @throws DomainRuleViolationException INFRA 분야가 아니거나 이미 완료된 협의일 때
     */
    public void completeAllocation(String staffUsername, Instant now) {
        if (field != ReviewField.INFRA) {
            throw new DomainRuleViolationException("할당 완료는 INFRA 분야 협의에서만 할 수 있습니다");
        }
        requireActor(staffUsername);
        if (status == ReviewStatus.COMPLETED) {
            throw new DomainRuleViolationException("이미 협의 완료된 분야입니다");
        }
        this.allocatedBy = staffUsername;
        this.allocatedAt = now;
        if (status == ReviewStatus.WAITING || status == ReviewStatus.IN_PROGRESS || status == ReviewStatus.RECHECK) {
            this.status = ReviewStatus.ALLOCATED;
        }
    }

    /**
     * 협의 완료. 체크리스트 전부 확인·의견 필수, 미해결 보완 없음, SECURITY 는 INFRA 할당 완료 이후(불변식 9).
     *
     * @param checklist 항목 키 → 확인 여부
     * @param checklistJson 저장용 직렬화 문자열
     * @param infraAllocated 같은 신청의 INFRA 협의가 할당 완료됐는지(SECURITY 에만 의미)
     * @param openSupplementCount 이 협의의 미해결(OPEN) 보완 요청 수
     */
    public void complete(
            String staffUsername,
            String opinion,
            Map<String, Boolean> checklist,
            String checklistJson,
            boolean infraAllocated,
            int openSupplementCount,
            Instant now) {
        requireActor(staffUsername);
        if (status == ReviewStatus.COMPLETED) {
            throw new DomainRuleViolationException("이미 협의 완료된 분야입니다");
        }
        if (status == ReviewStatus.SUPPLEMENT_REQUESTED || openSupplementCount > 0) {
            throw new DomainRuleViolationException("미해결 보완 요청이 있어 협의를 완료할 수 없습니다");
        }
        if (field == ReviewField.SECURITY && !infraAllocated) {
            throw new DomainRuleViolationException("INFRA 할당 완료 전에는 SECURITY 협의를 완료할 수 없습니다(INFRA 선행)");
        }
        if (opinion == null || opinion.isBlank()) {
            throw new DomainRuleViolationException("협의 완료에는 의견이 필요합니다");
        }
        ReviewChecklist.requireAllChecked(field, checklist);
        this.opinion = opinion.strip();
        this.checklist = checklistJson;
        this.completedBy = staffUsername;
        this.completedAt = now;
        this.status = ReviewStatus.COMPLETED;
    }

    /** 보완 요청을 보냈다 — 신청자 답변 전까지 완료 불가. */
    public void requestSupplement() {
        if (status == ReviewStatus.COMPLETED) {
            throw new DomainRuleViolationException("협의 완료된 분야에서는 보완을 요청할 수 없습니다. 차수 반환을 쓰세요");
        }
        this.status = ReviewStatus.SUPPLEMENT_REQUESTED;
    }

    /** 신청자가 보완에 답했다 → 담당자 재검토(검토 중)로 복귀. */
    public void onSupplementAnswered() {
        if (status == ReviewStatus.SUPPLEMENT_REQUESTED) {
            this.status = isAllocated() ? ReviewStatus.ALLOCATED : ReviewStatus.IN_PROGRESS;
        } else if (status == ReviewStatus.COMPLETED) {
            this.status = ReviewStatus.RECHECK;
        }
    }

    /** 중요 답변 변경·차수 반환 뒤 완료된 협의를 재검토 대상으로 돌린다(불변식 7·11). 의견·체크·할당은 보존한다. */
    public void markRecheck() {
        if (status == ReviewStatus.COMPLETED) {
            this.status = ReviewStatus.RECHECK;
            this.completedAt = null;
            this.completedBy = null;
        }
    }

    private static void requireActor(String username) {
        if (username == null || username.isBlank()) {
            throw new DomainRuleViolationException("담당자 없이는 협의 상태를 바꿀 수 없습니다");
        }
    }
}
