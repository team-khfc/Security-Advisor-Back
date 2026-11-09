package kr.securityadvisor.request.domain;

import jakarta.persistence.*;
import java.time.Instant;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ValueSource;
import lombok.Getter;

/** 최종 보고서 서술. 생성 당시 답변과 사람의 확인을 별도로 보존한다. */
@Entity
@Table(name = "final_drafts")
@Getter
public class FinalDraft extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "final_draft_id")
    private Long id;

    @Column(nullable = false)
    private Long securityRequestId;

    @Column(nullable = false, length = 64)
    private String sourceFingerprint;

    @Column(nullable = false, columnDefinition = "text")
    private String originalText;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ValueSource valueSource;

    @Column(length = 200)
    private String modelReference;

    @Column(length = 50)
    private String confirmedBy;

    private Instant confirmedAt;

    @Column(nullable = false)
    private boolean invalidated;

    @Version
    private long revision;

    protected FinalDraft() {}

    /** 생성은 출처와 관계없이 확인 전 상태다. */
    public static FinalDraft propose(
            Long requestId, String fingerprint, String text, ValueSource source, String model) {
        validateText(text);
        FinalDraft draft = new FinalDraft();
        draft.securityRequestId = requestId;
        draft.sourceFingerprint = fingerprint;
        draft.originalText = text.strip();
        draft.text = text.strip();
        draft.valueSource = source;
        draft.modelReference = model;
        return draft;
    }

    /** 오래된 원본을 확인 처리하거나 다른 창의 확인을 덮어쓰지 않는다. */
    public void confirm(String text, String actor, Instant now, long expectedRevision, String fingerprint) {
        if (revision != expectedRevision) throw new ConflictException("초안이 변경되었습니다. 최신 내용을 다시 확인해 주세요.");
        if (isStale(fingerprint)) throw new ConflictException("원본 답변이 변경되었습니다. 최신 답변으로 초안을 다시 작성해 주세요.");
        validateText(text);
        this.text = text.strip();
        confirmedBy = actor;
        confirmedAt = now;
    }

    public boolean isStale(String fingerprint) {
        return invalidated || !sourceFingerprint.equals(fingerprint);
    }

    public boolean isConfirmed() {
        return confirmedBy != null && confirmedAt != null;
    }

    public void invalidate() {
        invalidated = true;
    }

    /** 생성된 초안이 있으면 확인되지 않은 서술을 건너뛰어 제출할 수 없다. */
    public void requireSubmittable(String fingerprint) {
        if (isStale(fingerprint)) throw new DomainRuleViolationException("최종 초안의 원본 답변이 변경되었습니다. 초안을 다시 작성하고 확인해 주세요.");
        if (!isConfirmed()) throw new DomainRuleViolationException("최종 초안 내용을 확인한 뒤 제출해 주세요.");
    }

    private static void validateText(String text) {
        if (text == null || text.isBlank() || text.length() > 12000)
            throw new DomainRuleViolationException("최종 초안은 1~12,000자로 작성해 주세요.");
    }
}
