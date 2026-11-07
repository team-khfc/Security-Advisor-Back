package kr.securityadvisor.request.dto;

import java.time.Instant;
import java.util.List;
import kr.securityadvisor.request.domain.BusinessCase;

/**
 * 사업 건 상세. progressSummary 는 저장값이 아니라 하위 신청 상태에서 계산한 요약이다(불변식 15).
 */
public record BusinessCaseResponse(
        Long businessCaseId,
        String applicantUsername,
        String title,
        String summary,
        String department,
        Long publicationId,
        long revision,
        String progressSummary,
        List<SecurityRequestSummary> requests,
        List<AnswerResponse> caseAnswers,
        Instant createdAt,
        Instant updatedAt) {

    public static BusinessCaseResponse from(
            BusinessCase businessCase,
            String progressSummary,
            List<SecurityRequestSummary> requests,
            List<AnswerResponse> caseAnswers) {
        return new BusinessCaseResponse(
                businessCase.getId(),
                businessCase.getApplicantUsername(),
                businessCase.getTitle(),
                businessCase.getSummary(),
                businessCase.getDepartment(),
                businessCase.getPublicationId(),
                businessCase.getRevision(),
                progressSummary,
                requests,
                caseAnswers,
                businessCase.getCreatedAt(),
                businessCase.getUpdatedAt());
    }
}
