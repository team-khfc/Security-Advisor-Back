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
 * 1차 범위는 seed 게시본 1개로 운영하고 편집·게시 기능은 P2 다.
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

    /** JPA 전용 기본 생성자. 게시본은 1차 범위에서 seed 로만 만들어진다. */
    protected Publication() {}

    public Long getId() {
        return id;
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
