package kr.securityadvisor.request.dto;

import java.time.Instant;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.domain.SecurityRequest;

/** 신청 목록 한 줄(사업 건 정보 포함) — 역할별 "내 신청" 목록용. */
public record SecurityRequestListItem(
        Long securityRequestId,
        Long businessCaseId,
        String caseTitle,
        String department,
        String applicantUsername,
        DocTypeCode docType,
        int stage,
        RequestStatus status,
        String statusLabel,
        long revision,
        Instant updatedAt,
        Instant createdAt,
        RequestOverview overview) {

    public static SecurityRequestListItem from(SecurityRequest request, BusinessCase businessCase) {
        return new SecurityRequestListItem(
                request.getId(),
                businessCase.getId(),
                businessCase.getTitle(),
                businessCase.getDepartment(),
                businessCase.getApplicantUsername(),
                request.getDocType(),
                request.getStage(),
                request.getStatus(),
                request.getStatus().label(),
                request.getRevision(),
                request.getUpdatedAt(),
                request.getCreatedAt(),
                null);
    }
}
