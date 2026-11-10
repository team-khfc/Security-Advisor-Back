package kr.securityadvisor.llm.domain;

import jakarta.persistence.*;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 신청자 인터뷰 한 턴. 추천은 답변과 분리해 보관하고 명시적 적용 전에는 답변을 변경하지 않는다. */
@Entity
@Table(name = "interview_turns")
public class InterviewTurn extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interview_turn_id")
    private Long id;

    @Column(name = "business_case_id", nullable = false)
    private Long businessCaseId;

    @Column(name = "applicant_username", nullable = false, length = 50)
    private String applicantUsername;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String reply;

    @Column(name = "proposed_value", columnDefinition = "text")
    private String proposedValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", nullable = false)
    private String evidenceRefs;

    @Column(name = "context_fingerprint", nullable = false, length = 64)
    private String contextFingerprint;

    @Column(name = "based_revision", nullable = false)
    private long basedRevision;

    @Column(name = "answer_revision")
    private Long answerRevision;

    @Column(name = "model_reference", length = 200)
    private String modelReference;

    @Column(nullable = false)
    private boolean flagged;

    @Column(nullable = false)
    private boolean applied;

    @Version
    private long revision;

    protected InterviewTurn() {}

    public static InterviewTurn record(
            Long caseId,
            String applicant,
            String fieldKey,
            String mode,
            String question,
            String reply,
            String proposedValue,
            String evidenceRefs,
            String fingerprint,
            long basedRevision,
            Long answerRevision,
            String modelReference,
            boolean flagged) {
        if (!mode.equals("ASK") && !mode.equals("SUGGEST")) {
            throw new DomainRuleViolationException("지원하지 않는 인터뷰 요청입니다");
        }
        InterviewTurn turn = new InterviewTurn();
        turn.businessCaseId = caseId;
        turn.applicantUsername = applicant;
        turn.fieldKey = fieldKey;
        turn.mode = mode;
        turn.question = question;
        turn.reply = reply;
        turn.proposedValue = proposedValue;
        turn.evidenceRefs = evidenceRefs;
        turn.contextFingerprint = fingerprint;
        turn.basedRevision = basedRevision;
        turn.answerRevision = answerRevision;
        turn.modelReference = modelReference;
        turn.flagged = flagged;
        return turn;
    }

    /** 설명·차단된 응답을 신청서 값으로 적용하지 않는다. */
    public void markApplied() {
        if (!"SUGGEST".equals(mode) || flagged || proposedValue == null || proposedValue.isBlank()) {
            throw new DomainRuleViolationException("적용할 수 있는 추천답변이 없습니다");
        }
        applied = true;
    }

    public Long getId() {
        return id;
    }

    public Long getBusinessCaseId() {
        return businessCaseId;
    }

    public String getApplicantUsername() {
        return applicantUsername;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getMode() {
        return mode;
    }

    public String getQuestion() {
        return question;
    }

    public String getReply() {
        return reply;
    }

    public String getProposedValue() {
        return proposedValue;
    }

    public String getEvidenceRefs() {
        return evidenceRefs;
    }

    public String getContextFingerprint() {
        return contextFingerprint;
    }

    public long getBasedRevision() {
        return basedRevision;
    }

    public Long getAnswerRevision() {
        return answerRevision;
    }

    public String getModelReference() {
        return modelReference;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public boolean isApplied() {
        return applied;
    }
}
