package kr.securityadvisor.llm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 질의 대화 메시지. 생성 후 불변. */
@Entity
@Table(name = "inquiry_messages")
public class InquiryMessage extends BaseTimeEntity {

    public enum Role {
        USER,
        ASSISTANT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_message_id")
    private Long id;

    @Column(name = "inquiry_id", nullable = false)
    private Long inquiryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", nullable = false)
    private String evidenceRefs;

    @Column(name = "model_reference", length = 200)
    private String modelReference;

    @Column(nullable = false)
    private boolean flagged;

    protected InquiryMessage() {}

    private InquiryMessage(
            Long inquiryId, Role role, String text, String evidenceRefsJson, String modelReference, boolean flagged) {
        this.inquiryId = inquiryId;
        this.role = role;
        this.text = text;
        this.evidenceRefs = evidenceRefsJson;
        this.modelReference = modelReference;
        this.flagged = flagged;
    }

    public static InquiryMessage user(Long inquiryId, String text, String emptyRefsJson) {
        if (text == null || text.isBlank()) {
            throw new DomainRuleViolationException("질문이 비어 있습니다");
        }
        return new InquiryMessage(inquiryId, Role.USER, text.strip(), emptyRefsJson, null, false);
    }

    public static InquiryMessage assistant(
            Long inquiryId, String text, String evidenceRefsJson, String modelReference, boolean flagged) {
        return new InquiryMessage(
                inquiryId, Role.ASSISTANT, text == null ? "" : text.strip(), evidenceRefsJson, modelReference, flagged);
    }

    public Long getId() {
        return id;
    }

    public Long getInquiryId() {
        return inquiryId;
    }

    public Role getRole() {
        return role;
    }

    public String getText() {
        return text;
    }

    public String getEvidenceRefs() {
        return evidenceRefs;
    }

    public String getModelReference() {
        return modelReference;
    }

    public boolean isFlagged() {
        return flagged;
    }
}
