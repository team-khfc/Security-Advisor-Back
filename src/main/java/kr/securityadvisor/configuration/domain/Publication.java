package kr.securityadvisor.configuration.domain;

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
 * 기관 설정 게시본(F-08). 게시된 뒤에는 바뀌지 않으며 새 사업 건에만 적용된다(불변식 8).
 * seed 게시본 v1 로 시작하고, 관리자가 초안(Configuration)을 게시하면 새 버전이 추가된다.
 */
@Entity
@Table(name = "publications")
public class Publication extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "publication_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private int version;

    /** 필드 스키마 JSON 원문. 해석은 service 계층(FieldSchema)이 한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_schema", nullable = false)
    private String fieldSchemaJson;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by", length = 50)
    private String publishedBy;

    @Column(length = 500)
    private String note;

    @Column(name = "previous_version")
    private Integer previousVersion;

    /** JPA 전용 기본 생성자. */
    protected Publication() {}

    private Publication(int version, String fieldSchemaJson, Instant publishedAt) {
        this.version = version;
        this.fieldSchemaJson = fieldSchemaJson;
        // PostgreSQL 시각 정밀도와 생성 응답을 맞춰 게시 직후와 이후 조회가 같게 한다.
        this.publishedAt = publishedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    /** 초안 게시(F-08). 게시본은 생성 후 바뀌지 않는다. */
    public static Publication publish(int version, String fieldSchemaJson, Instant publishedAt) {
        if (version < 1 || fieldSchemaJson == null || fieldSchemaJson.isBlank()) {
            throw new IllegalArgumentException("게시본 버전·스키마가 필요합니다");
        }
        return new Publication(version, fieldSchemaJson, publishedAt);
    }

    public Long getId() {
        return id;
    }

    /** 게시 당시 관리자·메모·비교 기준을 스냅샷으로 남긴다. */
    public static Publication publish(
            int version, String schema, Instant at, String actor, String note, int previousVersion) {
        Publication publication = publish(version, schema, at);
        publication.publishedBy = actor;
        publication.note = note;
        publication.previousVersion = previousVersion;
        return publication;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public String getNote() {
        return note;
    }

    public Integer getPreviousVersion() {
        return previousVersion;
    }

    public int getVersion() {
        return version;
    }

    public String getFieldSchemaJson() {
        return fieldSchemaJson;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
