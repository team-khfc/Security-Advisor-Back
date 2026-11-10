package kr.securityadvisor.llm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 합성 연결 시험의 메타데이터. 키 값과 응답 원문은 보존하지 않는다. */
@Entity
@Table(name = "llm_connection_tests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmConnectionTest extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "llm_connection_test_id")
    private Long id;

    @Column(name = "llm_configuration_id")
    private Long configurationId;

    @Column(name = "configuration_revision")
    private Long configurationRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String snapshot;

    @Column(nullable = false, length = 40)
    private String outcome;

    @Column(name = "elapsed_millis", nullable = false)
    private long elapsedMillis;

    @Column(name = "tested_by", nullable = false, length = 50)
    private String testedBy;

    public static LlmConnectionTest record(
            Long configurationId, Long revision, String snapshot, String outcome, long elapsedMillis, String actor) {
        var test = new LlmConnectionTest();
        test.configurationId = configurationId;
        test.configurationRevision = revision;
        test.snapshot = snapshot;
        test.outcome = outcome;
        test.elapsedMillis = elapsedMillis;
        test.testedBy = actor;
        return test;
    }
}
