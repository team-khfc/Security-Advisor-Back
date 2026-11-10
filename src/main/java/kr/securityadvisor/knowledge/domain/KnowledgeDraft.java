package kr.securityadvisor.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.RevisionConflictException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 설치당 하나의 지식 초안. 행 잠금 아래에서 변경 순번을 올려 저장과 게시를 직렬화한다. */
@Entity
@Table(name = "knowledge_drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeDraft extends BaseTimeEntity {
    @Id
    @Column(name = "knowledge_draft_id")
    private Long id;

    @Column(name = "based_on_version", nullable = false)
    private int basedOnVersion;

    @Column(length = 500)
    private String note;

    @Version
    private long revision;

    @Column(name = "change_sequence", nullable = false)
    private long changeSequence;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    public void requireRevision(long expected) {
        if (expected != revision) throw new RevisionConflictException("지식 초안이 다른 곳에서 수정되었습니다", revision);
    }

    public void changed(String username) {
        updatedBy = username;
        touch();
    }

    public void updateNote(String value, String username) {
        note = value;
        changed(username);
    }

    public void published(int version, String username) {
        basedOnVersion = version;
        changed(username);
    }
    // 같은 사람이 같은 시각에 다른 항목을 저장해도 반드시 버전이 증가하도록 상태를 표시한다.
    private void touch() {
        changeSequence++;
    }
}
