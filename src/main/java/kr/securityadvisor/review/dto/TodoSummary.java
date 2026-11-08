package kr.securityadvisor.review.dto;

import kr.securityadvisor.common.security.MemberRole;

/**
 * "처리할 일 n건" 배지(F-17). 역할에 따라 채워지는 항목이 다르고 나머지는 0 이다.
 *
 * @param pendingReviews 담당자: 내게 배정된 2단계 협의 중 아직 협의 완료가 아닌 건
 * @param answeredSupplements 담당자: 내가 요청한 보완 중 신청자가 답변해 해소 확인을 기다리는 건
 * @param pendingFinalConfirmations 담당자: 내게 배정된 제출본 중 내 분야 최종 확인이 남은 건(SECURITY 는 INFRA 선행 후)
 * @param openSupplements 신청자: 내 신청에 걸린 미해결 보완 요청
 * @param finalReviews 신청자: 3단계 최종 검토 중인 내 신청
 * @param unassignedRequests 관리자: 2단계인데 어느 분야든 활성 배정이 없는 신청
 * @param total 위 항목의 합
 */
public record TodoSummary(
        MemberRole role,
        long pendingReviews,
        long answeredSupplements,
        long pendingFinalConfirmations,
        long openSupplements,
        long finalReviews,
        long unassignedRequests,
        long total) {

    public static TodoSummary of(
            MemberRole role,
            long pendingReviews,
            long answeredSupplements,
            long pendingFinalConfirmations,
            long openSupplements,
            long finalReviews,
            long unassignedRequests) {
        return new TodoSummary(
                role,
                pendingReviews,
                answeredSupplements,
                pendingFinalConfirmations,
                openSupplements,
                finalReviews,
                unassignedRequests,
                pendingReviews
                        + answeredSupplements
                        + pendingFinalConfirmations
                        + openSupplements
                        + finalReviews
                        + unassignedRequests);
    }
}
