package kr.securityadvisor.review.service;

import java.util.List;
import kr.securityadvisor.request.service.ReviewFactsProvider;
import kr.securityadvisor.review.domain.Review;
import kr.securityadvisor.review.domain.SupplementStatus;
import kr.securityadvisor.review.repository.ReviewRepository;
import kr.securityadvisor.review.repository.SupplementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** request 컨텍스트의 제출 전 검사 포트 구현 — 보완·협의 사실을 넘긴다. */
@Component
@RequiredArgsConstructor
public class ReviewFactsAdapter implements ReviewFactsProvider {

    private final ReviewRepository reviewRepository;
    private final SupplementRepository supplementRepository;

    @Override
    public int openSupplementCount(Long securityRequestId) {
        return supplementRepository.countBySecurityRequestIdAndStatus(securityRequestId, SupplementStatus.OPEN);
    }

    @Override
    public boolean allReviewsCompleted(Long securityRequestId) {
        List<Review> reviews = reviewRepository.findBySecurityRequestIdOrderByIdAsc(securityRequestId);
        return !reviews.isEmpty() && reviews.stream().allMatch(Review::isCompleted);
    }
}
