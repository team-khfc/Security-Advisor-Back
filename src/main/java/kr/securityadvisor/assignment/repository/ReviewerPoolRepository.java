package kr.securityadvisor.assignment.repository;

import java.util.List;
import java.util.Optional;
import kr.securityadvisor.assignment.domain.ReviewerPool;
import kr.securityadvisor.common.schema.ReviewField;
import org.springframework.data.jpa.repository.JpaRepository;

/** 분야별 담당자 목록 저장소. */
public interface ReviewerPoolRepository extends JpaRepository<ReviewerPool, Long> {

    Optional<ReviewerPool> findByFieldAndDefaultReviewerTrueAndActiveTrue(ReviewField field);

    List<ReviewerPool> findByFieldAndActiveTrueOrderByIdAsc(ReviewField field);

    Optional<ReviewerPool> findByFieldAndMemberUsernameAndActiveTrue(ReviewField field, String memberUsername);
}
