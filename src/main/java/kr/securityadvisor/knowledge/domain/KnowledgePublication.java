package kr.securityadvisor.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 게시 당시 공개 지식과 비공개 정보의 불변 사본. 원문은 관리자 전용 서비스만 노출한다. */
@Entity
@Table(name = "knowledge_publications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgePublication extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "knowledge_publication_id")
    private Long id;

    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String snapshot;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by", length = 50)
    private String publishedBy;

    @Column(length = 500)
    private String note;

    @Column(name = "previous_version")
    private Integer previousVersion;

    public static KnowledgePublication publish(
            int version, String snapshot, Instant at, String username, String note, Integer previous) {
        KnowledgePublication result = new KnowledgePublication();
        result.version = version;
        result.snapshot = snapshot;
        result.publishedAt = at.truncatedTo(ChronoUnit.MICROS);
        result.publishedBy = username;
        result.note = note;
        result.previousVersion = previous;
        return result;
    }
}
