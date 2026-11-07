package kr.securityadvisor.request.dto;

import java.time.Instant;
import kr.securityadvisor.request.domain.Submission;
import tools.jackson.databind.JsonNode;

/**
 * 제출본 응답. snapshot 은 제출 시점의 공통 값 + 서류별 값 + 설정 버전(불변).
 *
 * @param latest 이 신청의 가장 최근 제출본인지(4→3 반환 뒤 다시 제출하면 이전 제출본은 false)
 */
public record SubmissionResponse(
        Long submissionId,
        Long securityRequestId,
        Long publicationId,
        int sequenceNo,
        boolean latest,
        String submittedBy,
        Instant submittedAt,
        JsonNode snapshot) {

    public static SubmissionResponse from(Submission submission, boolean latest, JsonNode snapshot) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getSecurityRequestId(),
                submission.getPublicationId(),
                submission.getSequenceNo(),
                latest,
                submission.getSubmittedBy(),
                submission.getSubmittedAt(),
                snapshot);
    }
}
