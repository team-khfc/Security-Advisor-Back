package kr.securityadvisor.assignment.dto;

import kr.securityadvisor.assignment.domain.ReviewerPool;
import kr.securityadvisor.common.schema.ReassignmentReason;
import kr.securityadvisor.common.schema.ReviewField;

/** 분야별 담당자 목록 한 줄. */
public record ReviewerPoolResponse(
        Long reviewerPoolId,
        ReviewField field,
        String memberUsername,
        boolean defaultReviewer,
        boolean active,
        boolean eligible,
        ReassignmentReason unavailableReason) {

    public static ReviewerPoolResponse from(ReviewerPool pool, ReassignmentReason reason) {
        return new ReviewerPoolResponse(
                pool.getId(),
                pool.getField(),
                pool.getMemberUsername(),
                pool.isDefaultReviewer(),
                pool.isActive(),
                reason == null,
                reason);
    }
}
