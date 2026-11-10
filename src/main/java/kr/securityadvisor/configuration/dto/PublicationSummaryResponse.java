package kr.securityadvisor.configuration.dto;

import java.time.Instant;
import kr.securityadvisor.configuration.domain.Publication;

/** 게시 이력의 메타데이터. 본문과 변경 비교는 해당 게시본 ID로 별도 조회한다. */
public record PublicationSummaryResponse(
        Long publicationId,
        int version,
        Instant publishedAt,
        String publishedBy,
        String note,
        Integer previousVersion) {
    public static PublicationSummaryResponse from(Publication publication) {
        return new PublicationSummaryResponse(
                publication.getId(),
                publication.getVersion(),
                publication.getPublishedAt(),
                publication.getPublishedBy(),
                publication.getNote(),
                publication.getPreviousVersion());
    }
}
