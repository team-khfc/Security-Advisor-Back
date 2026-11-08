package kr.securityadvisor.configuration.domain;

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

/** 기관 설정 초안(F-08). 수정 초안 ≠ 게시본(원칙 4). 게시하면 Publication 이 새 버전으로 생기고 초안은 그 버전을 기준으로 남는다. */
@Entity
@Table(name = "configurations")
public class Configuration extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "configuration_id")
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_field_schema", nullable = false)
    private String draftFieldSchema;

    @Column(name = "based_on_version", nullable = false)
    private int basedOnVersion;

    @Column(length = 500)
    private String note;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private long revision;

    protected Configuration() {}

    private Configuration(String draftFieldSchema, int basedOnVersion, String updatedBy) {
        this.draftFieldSchema = draftFieldSchema;
        this.basedOnVersion = basedOnVersion;
        this.updatedBy = updatedBy;
    }

    /** 현재 게시본을 복사해 초안을 만든다. */
    public static Configuration startFrom(int publishedVersion, String fieldSchemaJson, String updatedBy) {
        requireActor(updatedBy);
        return new Configuration(fieldSchemaJson, publishedVersion, updatedBy);
    }

    /** 초안 저장 — 검증된 스키마 JSON 만 받는다(검증은 서비스). */
    public void saveDraft(String fieldSchemaJson, String note, String updatedBy) {
        requireActor(updatedBy);
        if (fieldSchemaJson == null || fieldSchemaJson.isBlank()) {
            throw new DomainRuleViolationException("초안 스키마가 비어 있습니다");
        }
        this.draftFieldSchema = fieldSchemaJson;
        this.note = note == null ? null : note.strip();
        this.updatedBy = updatedBy;
    }

    /** 게시 뒤 기준 버전을 올린다. */
    public void markPublished(int newVersion, String updatedBy) {
        requireActor(updatedBy);
        this.basedOnVersion = newVersion;
        this.updatedBy = updatedBy;
    }

    public void requireRevision(long expectedRevision) {
        if (expectedRevision != revision) {
            throw new ConflictException("설정 초안이 다른 곳에서 수정되었습니다(revision " + revision + ")");
        }
    }

    private static void requireActor(String username) {
        if (username == null || username.isBlank()) {
            throw new DomainRuleViolationException("설정을 바꾸는 관리자가 필요합니다");
        }
    }

    public Long getId() {
        return id;
    }

    public String getDraftFieldSchema() {
        return draftFieldSchema;
    }

    public int getBasedOnVersion() {
        return basedOnVersion;
    }

    public String getNote() {
        return note;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public long getRevision() {
        return revision;
    }
}
