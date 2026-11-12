package kr.securityadvisor.request.domain;

import jakarta.persistence.*;
import kr.securityadvisor.common.error.ConflictException;

/** 신청별 자료 검토 묶음. 파일을 삭제하지 않고 연결과 검토 상태의 이력을 별도로 관리한다. */
@Entity
@Table(name = "preparation_materials")
public class PreparationMaterials {
    @Id
    @Column(name = "security_request_id")
    private Long securityRequestId;

    @Column(nullable = false)
    private long revision;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    protected PreparationMaterials() {}

    public static PreparationMaterials create(Long id) {
        var value = new PreparationMaterials();
        value.securityRequestId = id;
        return value;
    }
    /** 호출자는 신청 잠금 안에서 revision을 비교한다. */
    public void replace(long expected, String content) {
        if (expected != revision) throw new ConflictException("준비자료가 변경됐습니다. 최신 목록을 확인한 뒤 다시 저장해 주세요.");
        this.content = content;
        revision++;
    }

    public long getRevision() {
        return revision;
    }

    public String getContent() {
        return content;
    }
}
