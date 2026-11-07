package kr.securityadvisor.request.dto;

import java.time.Instant;
import kr.securityadvisor.request.domain.Submission;

/** 제출본 요약(문서함 목록·신청 상세용). 스냅샷은 포함하지 않는다. */
public record SubmissionSummary(
        Long submissionId,
        Long securityRequestId,
        int sequenceNo,
        boolean latest,
        String submittedBy,
        Instant submittedAt) {

    public static SubmissionSummary from(Submission submission, boolean latest) {
        return new SubmissionSummary(
                submission.getId(),
                submission.getSecurityRequestId(),
                submission.getSequenceNo(),
                latest,
                submission.getSubmittedBy(),
                submission.getSubmittedAt());
    }
}
