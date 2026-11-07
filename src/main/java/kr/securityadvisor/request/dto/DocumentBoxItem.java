package kr.securityadvisor.request.dto;

import java.time.Instant;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.request.domain.RequestStatus;

/**
 * 문서함 한 줄(F-07): 제출본 + 그 신청·사업 건의 현재 상태. 확정 여부는 신청 상태(CONFIRMED)로 본다 — 서류 확정 ≠ 방화벽 적용.
 */
public record DocumentBoxItem(
        Long submissionId,
        int sequenceNo,
        boolean latest,
        Long securityRequestId,
        DocTypeCode docType,
        int stage,
        RequestStatus status,
        String statusLabel,
        Long businessCaseId,
        String caseTitle,
        String applicantUsername,
        String submittedBy,
        Instant submittedAt) {}
