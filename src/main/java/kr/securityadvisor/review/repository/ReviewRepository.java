package kr.securityadvisor.review.repository;

import java.util.List;
import java.util.Optional;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.review.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;

/** 협의 저장소. */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findBySecurityRequestIdOrderByIdAsc(Long securityRequestId);

    Optional<Review> findBySecurityRequestIdAndField(Long securityRequestId, ReviewField field);

    List<Review> findBySecurityRequestIdInOrderByIdAsc(List<Long> securityRequestIds);
}
