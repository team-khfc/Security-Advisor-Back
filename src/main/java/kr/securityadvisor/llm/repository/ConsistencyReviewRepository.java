package kr.securityadvisor.llm.repository;

import java.util.List;
import kr.securityadvisor.llm.domain.ConsistencyReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsistencyReviewRepository extends JpaRepository<ConsistencyReview, Long> {
    List<ConsistencyReview> findBySecurityRequestIdOrderByIdDesc(Long id, Pageable page);
}
