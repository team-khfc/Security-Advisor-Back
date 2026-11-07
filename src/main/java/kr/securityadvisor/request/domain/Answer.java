package kr.securityadvisor.request.domain;

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
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.ValueSource;

/**
 * 인터뷰 답변(필드 단위). 범위에 따라 사업 건(CASE) 또는 신청(REQUEST)에 속한다.
 *
 * <p>모든 값은 출처를 가진다(불변 원칙 2). 출처가 없는 항목은 "담당자 확인 필요"다. AI 추론 값은 근거가 있어야 하고
 * 사용자·담당자가 확인하기 전까지 미확정이며(불변식 1), STAFF_CONFIRMED 전이는 확인 주체가 있어야 한다(불변식 5).
 */
@Entity
@Table(name = "answers")
public class Answer extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AnswerScope scope;

    @Column(name = "business_case_id", nullable = false)
    private Long businessCaseId;

    /** REQUEST 범위에서만 값이 있다. */
    @Column(name = "security_request_id")
    private Long securityRequestId;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(columnDefinition = "text")
    private String value;

    /** null 이면 "담당자 확인 필요". */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_source", length = 20)
    private ValueSource valueSource;

    /** AI 추론 근거(원문 위치·요약). AI_INFERRED 이면 필수. */
    @Column(columnDefinition = "text")
    private String evidence;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(name = "confirmed_by", length = 50)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** 앞선 답변 변경·차수 반환으로 다시 봐야 하는 항목(F-03·F-05). */
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Version
    @Column(nullable = false)
    private long revision;

    /** JPA 전용 기본 생성자. */
    protected Answer() {}

    private Answer(AnswerScope scope, Long businessCaseId, Long securityRequestId, String fieldKey) {
        if (scope == AnswerScope.REQUEST && securityRequestId == null) {
            throw new DomainRuleViolationException("서류별 답변에는 신청 ID 가 필요합니다");
        }
        if (scope == AnswerScope.CASE && securityRequestId != null) {
            throw new DomainRuleViolationException("공통 답변은 신청에 속하지 않습니다");
        }
        this.scope = scope;
        this.businessCaseId = businessCaseId;
        this.securityRequestId = securityRequestId;
        this.fieldKey = fieldKey;
    }

    /** 사용자 답변. 본인이 적은 값이므로 확인된 값으로 본다. */
    public static Answer userAnswer(
            AnswerScope scope, Long businessCaseId, Long securityRequestId, String fieldKey, String value) {
        Answer answer = new Answer(scope, businessCaseId, securityRequestId, fieldKey);
        answer.applyUserValue(value);
        return answer;
    }

    /** AI 추론 값 — 근거 필수, 확인 전까지 미확정. */
    public static Answer aiInferred(
            AnswerScope scope,
            Long businessCaseId,
            Long securityRequestId,
            String fieldKey,
            String value,
            String evidence) {
        Answer answer = new Answer(scope, businessCaseId, securityRequestId, fieldKey);
        answer.applyAiValue(value, evidence);
        return answer;
    }

    /** 값 없는 자리 표시 — 담당자 확인 필요(기관 내부 사실 등). */
    public static Answer staffCheckNeeded(
            AnswerScope scope, Long businessCaseId, Long securityRequestId, String fieldKey) {
        Answer answer = new Answer(scope, businessCaseId, securityRequestId, fieldKey);
        answer.markStaffCheckNeeded();
        return answer;
    }

    public Long getId() {
        return id;
    }

    public AnswerScope getScope() {
        return scope;
    }

    public Long getBusinessCaseId() {
        return businessCaseId;
    }

    public Long getSecurityRequestId() {
        return securityRequestId;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getValue() {
        return value;
    }

    public ValueSource getValueSource() {
        return valueSource;
    }

    public String getEvidence() {
        return evidence;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public long getRevision() {
        return revision;
    }

    /** 값이 있는지(빈 문자열은 없는 것으로 본다). */
    public boolean hasValue() {
        return value != null && !value.isBlank();
    }

    /** 제출을 막는 미확정 항목인지 — 출처 없음(담당자 확인 필요) 또는 확인되지 않은 AI 값. */
    public boolean isUnconfirmed() {
        return valueSource == null || !confirmed;
    }

    /**
     * @throws ConflictException expectedRevision 이 현재 revision 과 다를 때(409)
     */
    public void requireRevision(long expectedRevision) {
        if (expectedRevision != revision) {
            throw new ConflictException("답변이 다른 곳에서 수정되었습니다. 최신 값(revision " + revision + ")을 다시 확인하세요");
        }
    }

    /** 사용자가 값을 적거나 고친다. 이전 출처가 무엇이든 사용자 답변으로 바뀌고 재검토 표시는 해제된다. */
    public void updateByUser(String value) {
        applyUserValue(value);
    }

    /** AI 가 값을 제안한다. 이미 확인된 값은 덮어쓰지 않는다(AI 는 제안만, 불변 원칙 1). */
    public void proposeByAi(String value, String evidence) {
        if (confirmed && hasValue()) {
            throw new DomainRuleViolationException("확인된 값은 AI 제안으로 덮어쓸 수 없습니다: " + fieldKey);
        }
        applyAiValue(value, evidence);
    }

    /** 사용자가 AI 제안 값을 확인한다. 출처는 AI_INFERRED 로 남고 확인 표시만 붙는다. */
    public void confirmByUser(String username, Instant now) {
        if (valueSource != ValueSource.AI_INFERRED) {
            throw new DomainRuleViolationException("AI 제안 값만 사용자가 확인할 수 있습니다: " + fieldKey);
        }
        confirm(username, now);
    }

    /** 담당자가 값을 입력한다(STAFF_INPUT — 예: 서버·IP·포트 할당). 입력자가 곧 확인자다. */
    public void inputByStaff(String staffUsername, String value, Instant now) {
        requireActor(staffUsername);
        this.value = value;
        this.valueSource = ValueSource.STAFF_INPUT;
        this.evidence = null;
        confirm(staffUsername, now);
    }

    /** 담당자가 기존 값(사용자 답변·AI 추론)을 확인한다 → STAFF_CONFIRMED. 확인 주체가 없으면 전이 불가(불변식 5). */
    public void confirmByStaff(String staffUsername, Instant now) {
        requireActor(staffUsername);
        if (!hasValue()) {
            throw new DomainRuleViolationException("값이 없는 항목은 확인할 수 없습니다: " + fieldKey);
        }
        this.valueSource = ValueSource.STAFF_CONFIRMED;
        confirm(staffUsername, now);
    }

    /** 사용자가 모르는 항목을 담당자 확인 요청으로 넘긴다. 기존 값은 지우지 않고 출처만 비운다. */
    public void markStaffCheckNeeded() {
        this.valueSource = null;
        this.evidence = null;
        this.confirmed = false;
        this.confirmedBy = null;
        this.confirmedAt = null;
    }

    /** 앞선 답변 변경·차수 반환의 영향 항목으로 표시한다. 값·출처·확인은 보존한다(불변식 11). */
    public void markNeedsReview() {
        this.needsReview = true;
    }

    public void clearNeedsReview() {
        this.needsReview = false;
    }

    private void applyUserValue(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException("답변 값이 비어 있습니다: " + fieldKey);
        }
        this.value = value.strip();
        this.valueSource = ValueSource.USER_ANSWER;
        this.evidence = null;
        this.confirmed = true;
        this.confirmedBy = null;
        this.confirmedAt = null;
        this.needsReview = false;
    }

    private void applyAiValue(String value, String evidence) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException("AI 제안 값이 비어 있습니다: " + fieldKey);
        }
        if (evidence == null || evidence.isBlank()) {
            throw new DomainRuleViolationException("AI 제안 값에는 근거가 필요합니다: " + fieldKey);
        }
        this.value = value.strip();
        this.valueSource = ValueSource.AI_INFERRED;
        this.evidence = evidence.strip();
        this.confirmed = false;
        this.confirmedBy = null;
        this.confirmedAt = null;
    }

    private void confirm(String username, Instant now) {
        this.confirmed = true;
        this.confirmedBy = username;
        this.confirmedAt = now;
        this.needsReview = false;
    }

    private static void requireActor(String username) {
        if (username == null || username.isBlank()) {
            throw new DomainRuleViolationException("확인 주체(담당자) 없이는 전이할 수 없습니다");
        }
    }
}
