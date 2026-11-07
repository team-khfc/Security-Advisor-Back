package kr.securityadvisor.review.repository;

import java.util.List;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.review.domain.FinalConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

/** 최종 확인 저장소. 추가·조회만. */
public interface FinalConfirmationRepository extends JpaRepository<FinalConfirmation, Long> {

    List<FinalConfirmation> findBySubmissionIdOrderByConfirmedAtAscIdAsc(Long submissionId);

    boolean existsBySubmissionIdAndField(Long submissionId, ReviewField field);
}
