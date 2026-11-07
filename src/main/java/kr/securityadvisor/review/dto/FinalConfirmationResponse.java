package kr.securityadvisor.review.dto;

import java.time.Instant;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.review.domain.FinalConfirmation;

/** 최종 확인 응답. */
public record FinalConfirmationResponse(
        Long finalConfirmationId,
        Long submissionId,
        Long securityRequestId,
        ReviewField field,
        String opinion,
        String confirmedBy,
        Instant confirmedAt) {

    public static FinalConfirmationResponse from(FinalConfirmation confirmation) {
        return new FinalConfirmationResponse(
                confirmation.getId(),
                confirmation.getSubmissionId(),
                confirmation.getSecurityRequestId(),
                confirmation.getField(),
                confirmation.getOpinion(),
                confirmation.getConfirmedBy(),
                confirmation.getConfirmedAt());
    }
}
