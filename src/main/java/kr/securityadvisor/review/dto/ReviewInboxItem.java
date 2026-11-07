package kr.securityadvisor.review.dto;

import java.time.LocalDate;
import kr.securityadvisor.request.dto.SecurityRequestSummary;

/**
 * 담당자 요청함 한 줄 — 내게 배정된 분야 협의와 그 신청·사업 건 요약.
 *
 * @param reviewDueDate 이 분야 협의 기한일(활성 기한이 없으면 null)
 */
public record ReviewInboxItem(
        ReviewResponse review,
        SecurityRequestSummary request,
        Long businessCaseId,
        String caseTitle,
        String applicantUsername,
        LocalDate reviewDueDate) {}
