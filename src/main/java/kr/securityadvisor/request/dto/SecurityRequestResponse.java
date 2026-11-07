package kr.securityadvisor.request.dto;

import java.time.Instant;
import java.util.List;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.domain.SecurityRequest;

/**
 * 신청 상세. 서류별 답변(scope=REQUEST)만 담고, 공통 답변은 사업 건 응답에서 가져온다.
 */
public record SecurityRequestResponse(
        Long securityRequestId,
        Long businessCaseId,
        DocTypeCode docType,
        int stage,
        RequestStatus status,
        String statusLabel,
        long revision,
        List<AnswerResponse> answers,
        Instant createdAt,
        Instant updatedAt) {

    public static SecurityRequestResponse from(SecurityRequest request, List<AnswerResponse> answers) {
        return new SecurityRequestResponse(
                request.getId(),
                request.getBusinessCaseId(),
                request.getDocType(),
                request.getStage(),
                request.getStatus(),
                request.getStatus().label(),
                request.getRevision(),
                answers,
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
