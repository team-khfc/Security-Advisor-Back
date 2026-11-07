package kr.securityadvisor.request.service;

/**
 * 제출 전 검사에 필요한 협의 쪽 사실을 제공하는 포트(request → review 의존을 만들지 않기 위한 역전). 구현은 review 컨텍스트에 있다.
 */
public interface ReviewFactsProvider {

    /** 미해결(OPEN) 보완 요청 수. */
    int openSupplementCount(Long securityRequestId);

    /** 필요한 분야의 협의가 모두 완료됐는지(협의가 하나도 없으면 false). */
    boolean allReviewsCompleted(Long securityRequestId);
}
