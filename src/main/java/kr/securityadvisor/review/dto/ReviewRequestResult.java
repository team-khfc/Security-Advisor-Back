package kr.securityadvisor.review.dto;

import java.util.List;
import kr.securityadvisor.assignment.dto.AssignmentSummary;
import kr.securityadvisor.request.dto.DeadlineResponse;
import kr.securityadvisor.request.dto.SecurityRequestSummary;

/**
 * 협의 요청 결과: 2단계로 넘어간 신청, 열린 협의, 자동 배정 결과(미배정 분야 포함), 생성된 협의 기한.
 */
public record ReviewRequestResult(
        SecurityRequestSummary request,
        List<ReviewResponse> reviews,
        AssignmentSummary assignments,
        List<DeadlineResponse> deadlines) {}
