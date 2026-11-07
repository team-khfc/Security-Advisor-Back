package kr.securityadvisor.review.repository;

import java.util.List;
import kr.securityadvisor.review.domain.Supplement;
import kr.securityadvisor.review.domain.SupplementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/** 보완 요청 저장소. */
public interface SupplementRepository extends JpaRepository<Supplement, Long> {

    List<Supplement> findBySecurityRequestIdOrderByRequestedAtAscIdAsc(Long securityRequestId);

    int countBySecurityRequestIdAndStatus(Long securityRequestId, SupplementStatus status);

    int countByReviewIdAndStatus(Long reviewId, SupplementStatus status);

    List<Supplement> findByReviewIdAndStatus(Long reviewId, SupplementStatus status);
}
