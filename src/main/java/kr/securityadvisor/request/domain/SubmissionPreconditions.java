package kr.securityadvisor.request.domain;

import java.util.List;

/**
 * 제출 전 검사에 필요한 사실(F-07). 답변·협의·보완은 다른 엔티티·컨텍스트에 있으므로 애플리케이션 서비스가 모아서 넘기고,
 * 판단은 {@link SecurityRequest#submit} 이 한다.
 *
 * @param missingRequiredFields 값이 없는 필수 항목 키
 * @param unconfirmedFields 미확정 항목 키(AI 추론 미확인, 담당자 확인 필요)
 * @param openSupplementCount 미해결 보완 요청 수
 * @param allReviewsCompleted 필요한 분야(INFRA·SECURITY)의 협의가 모두 완료됐는지 — 할당 완료가 아니라 협의 완료 기준(불변식 12)
 */
public record SubmissionPreconditions(
        List<String> missingRequiredFields,
        List<String> unconfirmedFields,
        int openSupplementCount,
        boolean allReviewsCompleted) {

    public static SubmissionPreconditions satisfied() {
        return new SubmissionPreconditions(List.of(), List.of(), 0, true);
    }
}
