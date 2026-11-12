package kr.securityadvisor.review.dto;

import kr.securityadvisor.common.security.MemberRole;

/**
 * "처리할 일 n건" 배지(F-17). 역할에 따라 채워지는 항목이 다르고 나머지는 0 이다.
 *
 * @param pendingReviews 담당자: 내게 배정된 2단계 협의 중 아직 협의 완료가 아닌 건
 * @param answeredSupplements 담당자: 현재 내 분야 배정의 보완 중 답변 검토·해소가 남은 건(인계 포함)
 * @param pendingFinalConfirmations 담당자: 내 차례의 최종 확인 또는 후행 분야의 서류 확정이 남은 건
 * @param openSupplements 신청자: 내 신청에 걸린 미해결 보완 요청
 * @param finalReviews 신청자: 3단계 최종 검토 중인 내 신청
 * @param unassignedRequests 관리자: 2~4단계 진행 중인 신청에서 필요한 분야의 담당자가 미배정 또는 부적격인 신청
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
        long preparationReviews,
        long preparationSupplements,
        long total) {

    public static TodoSummary of(
            MemberRole role,
            long pendingReviews,
            long answeredSupplements,
            long pendingFinalConfirmations,
            long openSupplements,
            long finalReviews,
            long unassignedRequests,
            long preparationReviews,
            long preparationSupplements) {
        return new TodoSummary(
                role,
                pendingReviews,
                answeredSupplements,
                pendingFinalConfirmations,
                openSupplements,
                finalReviews,
                unassignedRequests,
                preparationReviews,
                preparationSupplements,
                pendingReviews
                        + answeredSupplements
                        + pendingFinalConfirmations
                        + openSupplements
                        + finalReviews
                        + unassignedRequests
                        + preparationReviews
                        + preparationSupplements);
    }
}
