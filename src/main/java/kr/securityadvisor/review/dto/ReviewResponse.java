package kr.securityadvisor.review.dto;

import java.time.Instant;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.review.domain.Review;
import kr.securityadvisor.review.domain.ReviewStatus;

/** 분야별 협의 응답. */
public record ReviewResponse(
        Long reviewId,
        Long securityRequestId,
        ReviewField field,
        ReviewStatus status,
        String statusLabel,
        String opinion,
        String completedBy,
        Instant completedAt,
        long revision) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getSecurityRequestId(),
                review.getField(),
                review.getStatus(),
                review.getStatus().label(),
                review.getOpinion(),
                review.getCompletedBy(),
                review.getCompletedAt(),
                review.getRevision());
    }
}
