package kr.securityadvisor.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 공개자료 발췌(지식, F-09). 출처·판본·위치를 반드시 가진다 — AI 제안의 근거 표시가 여기서 나온다.
 * 검색용 search_vector 는 DB 생성 컬럼이라 매핑하지 않는다.
 */
@Entity
@Table(name = "knowledge_snippets")
public class KnowledgeSnippet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "knowledge_snippet_id")
    private Long id;

    @Column(name = "source_title", nullable = false, length = 200)
    private String sourceTitle;

    @Column(name = "source_version", nullable = false, length = 50)
    private String sourceVersion;

    @Column(name = "source_locator", nullable = false, length = 200)
    private String sourceLocator;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_keys", nullable = false)
    private String fieldKeys;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "doc_types", nullable = false)
    private String docTypes;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long revision;

    protected KnowledgeSnippet() {}

    private KnowledgeSnippet(
            String sourceTitle,
            String sourceVersion,
            String sourceLocator,
            String fieldKeysJson,
            String docTypesJson,
            String body) {
        this.sourceTitle = sourceTitle;
        this.sourceVersion = sourceVersion;
        this.sourceLocator = sourceLocator;
        this.fieldKeys = fieldKeysJson;
        this.docTypes = docTypesJson;
        this.body = body;
    }

    public static KnowledgeSnippet create(
            String sourceTitle,
            String sourceVersion,
            String sourceLocator,
            String fieldKeysJson,
            boolean hasFieldKeys,
            String docTypesJson,
            String body) {
        requireText(sourceTitle, "출처 제목");
        requireText(sourceVersion, "출처 판본");
        requireText(sourceLocator, "출처 위치");
        requireText(body, "본문");
        if (!hasFieldKeys) {
            throw new DomainRuleViolationException("공개 지식에는 적용 질문이 하나 이상 필요합니다");
        }
        return new KnowledgeSnippet(
                sourceTitle.strip(),
                sourceVersion.strip(),
                sourceLocator.strip(),
                fieldKeysJson,
                docTypesJson,
                body.strip());
    }

    public void update(
            String sourceTitle,
            String sourceVersion,
            String sourceLocator,
            String fieldKeysJson,
            boolean hasFieldKeys,
            String docTypesJson,
            String body,
            boolean active) {
        requireText(sourceTitle, "출처 제목");
        requireText(sourceVersion, "출처 판본");
        requireText(sourceLocator, "출처 위치");
        requireText(body, "본문");
        if (!hasFieldKeys) {
            throw new DomainRuleViolationException("공개 지식에는 적용 질문이 하나 이상 필요합니다");
        }
        this.sourceTitle = sourceTitle.strip();
        this.sourceVersion = sourceVersion.strip();
        this.sourceLocator = sourceLocator.strip();
        this.fieldKeys = fieldKeysJson;
        this.docTypes = docTypesJson;
        this.body = body.strip();
        this.active = active;
    }

    public void requireRevision(long expectedRevision) {
        if (expectedRevision != revision) {
            throw new ConflictException("공개 지식이 다른 곳에서 수정되었습니다(수정본 " + revision + ")");
        }
    }

    /** 화면·프롬프트에 쓰는 출처 표기. */
    public String citation() {
        return sourceTitle + " " + sourceVersion + " " + sourceLocator;
    }

    public Long getId() {
        return id;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public String getSourceLocator() {
        return sourceLocator;
    }

    public String getFieldKeys() {
        return fieldKeys;
    }

    public String getDocTypes() {
        return docTypes;
    }

    public String getBody() {
        return body;
    }

    public boolean isActive() {
        return active;
    }

    public long getRevision() {
        return revision;
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException(what + "은(는) 비어 있을 수 없습니다");
        }
    }
}
