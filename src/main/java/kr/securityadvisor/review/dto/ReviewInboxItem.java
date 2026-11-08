package kr.securityadvisor.review.dto;

import java.time.LocalDate;
import java.util.List;
import kr.securityadvisor.request.dto.SecurityRequestSummary;

/**
 * 담당자 요청함 한 줄 — 내게 배정된 분야 협의와 그 신청·사업 건 요약.
 *
 * @param reviewDueDate 이 분야 협의 기한일(활성 기한이 없으면 null)
 * @param openSupplementCount 이 신청에 걸린 미해결 보완 요청 수
 * @param siblingRequests 같은 사업 건의 다른 서류(신청) 요약 — 함께 보여 공통 정보를 한 번에 다루게 한다
 */
public record ReviewInboxItem(
        ReviewResponse review,
        SecurityRequestSummary request,
        Long businessCaseId,
        String caseTitle,
        String applicantUsername,
        LocalDate reviewDueDate,
        int openSupplementCount,
        List<SecurityRequestSummary> siblingRequests) {}
