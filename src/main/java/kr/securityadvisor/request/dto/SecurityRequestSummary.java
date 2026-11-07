package kr.securityadvisor.request.dto;

import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.domain.SecurityRequest;

/** 사업 건 화면의 하위 신청 요약 한 줄. */
public record SecurityRequestSummary(
        Long securityRequestId,
        DocTypeCode docType,
        int stage,
        RequestStatus status,
        String statusLabel,
        long revision) {

    public static SecurityRequestSummary from(SecurityRequest request) {
        return new SecurityRequestSummary(
                request.getId(),
                request.getDocType(),
                request.getStage(),
                request.getStatus(),
                request.getStatus().label(),
                request.getRevision());
    }
}
