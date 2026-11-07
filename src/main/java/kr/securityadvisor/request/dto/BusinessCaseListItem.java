package kr.securityadvisor.request.dto;

import java.time.Instant;
import java.util.List;
import kr.securityadvisor.request.domain.BusinessCase;

/** 내 사업 건 목록의 한 줄. */
public record BusinessCaseListItem(
        Long businessCaseId,
        String title,
        String department,
        String progressSummary,
        List<SecurityRequestSummary> requests,
        Instant createdAt) {

    public static BusinessCaseListItem from(
            BusinessCase businessCase, String progressSummary, List<SecurityRequestSummary> requests) {
        return new BusinessCaseListItem(
                businessCase.getId(),
                businessCase.getTitle(),
                businessCase.getDepartment(),
                progressSummary,
                requests,
                businessCase.getCreatedAt());
    }
}
