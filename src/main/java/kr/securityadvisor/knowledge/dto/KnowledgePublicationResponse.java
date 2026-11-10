package kr.securityadvisor.knowledge.dto;

import java.time.Instant;
import kr.securityadvisor.knowledge.domain.KnowledgePublication;

/** 메모·게시자까지 포함한 관리용 메타데이터. 일반 사용자에게는 별도 버전 정보만 반환한다. */
public record KnowledgePublicationResponse(
        Long knowledgePublicationId,
        int version,
        Instant publishedAt,
        String publishedBy,
        String note,
        Integer previousVersion) {
    public static KnowledgePublicationResponse from(KnowledgePublication p) {
        return new KnowledgePublicationResponse(
                p.getId(), p.getVersion(), p.getPublishedAt(), p.getPublishedBy(), p.getNote(), p.getPreviousVersion());
    }
}
