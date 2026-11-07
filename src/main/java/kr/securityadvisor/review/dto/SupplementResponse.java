package kr.securityadvisor.review.dto;

import java.time.Instant;
import java.util.List;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.review.domain.Supplement;
import kr.securityadvisor.review.domain.SupplementStatus;

/** 보완 요청 응답. */
public record SupplementResponse(
        Long supplementId,
        Long securityRequestId,
        Long reviewId,
        ReviewField field,
        List<String> fieldKeys,
        String reason,
        SupplementStatus status,
        String statusLabel,
        String requestedBy,
        Instant requestedAt,
        String answerNote,
        Instant answeredAt,
        String resolvedBy,
        Instant resolvedAt,
        long revision) {

    public static SupplementResponse from(Supplement supplement, List<String> fieldKeys) {
        return new SupplementResponse(
                supplement.getId(),
                supplement.getSecurityRequestId(),
                supplement.getReviewId(),
                supplement.getField(),
                fieldKeys,
                supplement.getReason(),
                supplement.getStatus(),
                supplement.getStatus().label(),
                supplement.getRequestedBy(),
                supplement.getRequestedAt(),
                supplement.getAnswerNote(),
                supplement.getAnsweredAt(),
                supplement.getResolvedBy(),
                supplement.getResolvedAt(),
                supplement.getRevision());
    }
}
