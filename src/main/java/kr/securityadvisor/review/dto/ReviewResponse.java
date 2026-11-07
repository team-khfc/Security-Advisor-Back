package kr.securityadvisor.review.dto;

import java.time.Instant;
import java.util.Map;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.review.domain.Review;
import kr.securityadvisor.review.domain.ReviewStatus;

/** 분야별 협의 응답. allocated 는 할당 완료 여부(협의 완료와 별개). */
public record ReviewResponse(
        Long reviewId,
        Long securityRequestId,
        ReviewField field,
        ReviewStatus status,
        String statusLabel,
        boolean allocated,
        String allocatedBy,
        Instant allocatedAt,
        Map<String, Boolean> checklist,
        String opinion,
        String completedBy,
        Instant completedAt,
        long revision) {

    public static ReviewResponse from(Review review, Map<String, Boolean> checklist) {
        return new ReviewResponse(
                review.getId(),
                review.getSecurityRequestId(),
                review.getField(),
                review.getStatus(),
                review.getStatus().label(),
                review.isAllocated(),
                review.getAllocatedBy(),
                review.getAllocatedAt(),
                checklist,
                review.getOpinion(),
                review.getCompletedBy(),
                review.getCompletedAt(),
                review.getRevision());
    }
}
