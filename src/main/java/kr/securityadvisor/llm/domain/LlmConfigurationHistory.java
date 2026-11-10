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

/** 구성 변경의 추가 전용 사본. 복원으로도 과거 행을 수정하지 않는다. */
@Entity
@Table(name = "llm_configuration_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmConfigurationHistory extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "llm_configuration_history_id")
    private Long id;

    @Column(name = "llm_configuration_id", nullable = false)
    private Long configurationId;

    @Column(name = "configuration_revision", nullable = false)
    private long configurationRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String snapshot;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Column(length = 500)
    private String reason;

    @Column(name = "restored_from_id")
    private Long restoredFromId;

    public static LlmConfigurationHistory record(
            Long id, long revision, String snapshot, String action, String actor, String reason, Long restoredFromId) {
        var h = new LlmConfigurationHistory();
        h.configurationId = id;
        h.configurationRevision = revision;
        h.snapshot = snapshot;
        h.action = action;
        h.changedBy = actor;
        h.reason = reason;
        h.restoredFromId = restoredFromId;
        return h;
    }
}
