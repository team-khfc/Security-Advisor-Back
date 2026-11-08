package kr.securityadvisor.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 렌더링된 서류(F-06). 생성 후 불변. AI 보완 서술은 값이 아니라 미확정 참고 문장이다(원칙 1). */
@Entity
@Table(name = "generated_documents")
public class GeneratedDocument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generated_document_id")
    private Long id;

    @Column(name = "security_request_id", nullable = false)
    private Long securityRequestId;

    @Column(name = "submission_id")
    private Long submissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentKind kind;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 200)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String sections;

    @Column(name = "rendered_markdown", nullable = false, columnDefinition = "text")
    private String renderedMarkdown;

    @Column(name = "ai_supplement", columnDefinition = "text")
    private String aiSupplement;

    @Column(name = "model_reference", length = 200)
    private String modelReference;

    @Column(name = "unconfirmed_count", nullable = false)
    private int unconfirmedCount;

    @Column(name = "generated_by", nullable = false, length = 50)
    private String generatedBy;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected GeneratedDocument() {}

    private GeneratedDocument(
            Long securityRequestId,
            Long submissionId,
            DocumentKind kind,
            int version,
            String title,
            String sectionsJson,
            String renderedMarkdown,
            String aiSupplement,
            String modelReference,
            int unconfirmedCount,
            String generatedBy,
            Instant generatedAt) {
        this.securityRequestId = securityRequestId;
        this.submissionId = submissionId;
        this.kind = kind;
        this.version = version;
        this.title = title;
        this.sections = sectionsJson;
        this.renderedMarkdown = renderedMarkdown;
        this.aiSupplement = aiSupplement;
        this.modelReference = modelReference;
        this.unconfirmedCount = unconfirmedCount;
        this.generatedBy = generatedBy;
        this.generatedAt = generatedAt;
    }

    public static GeneratedDocument render(
            Long securityRequestId,
            Long submissionId,
            DocumentKind kind,
            int version,
            String title,
            String sectionsJson,
            String renderedMarkdown,
            String aiSupplement,
            String modelReference,
            int unconfirmedCount,
            String generatedBy,
            Instant generatedAt) {
        if (kind != DocumentKind.DRAFT && submissionId == null) {
            throw new DomainRuleViolationException("제출본·확정본 서류는 제출본에서만 만들 수 있습니다");
        }
        if (renderedMarkdown == null || renderedMarkdown.isBlank()) {
            throw new DomainRuleViolationException("렌더링 결과가 비어 있습니다");
        }
        return new GeneratedDocument(
                securityRequestId,
                submissionId,
                kind,
                version,
                title,
                sectionsJson,
                renderedMarkdown,
                aiSupplement,
                modelReference,
                unconfirmedCount,
                generatedBy,
                generatedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getSecurityRequestId() {
        return securityRequestId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public DocumentKind getKind() {
        return kind;
    }

    public int getVersion() {
        return version;
    }

    public String getTitle() {
        return title;
    }

    public String getSections() {
        return sections;
    }

    public String getRenderedMarkdown() {
        return renderedMarkdown;
    }

    public String getAiSupplement() {
        return aiSupplement;
    }

    public String getModelReference() {
        return modelReference;
    }

    public int getUnconfirmedCount() {
        return unconfirmedCount;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
