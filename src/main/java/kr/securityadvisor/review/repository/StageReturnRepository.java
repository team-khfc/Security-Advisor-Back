package kr.securityadvisor.review.repository;

import java.util.List;
import kr.securityadvisor.review.domain.StageReturn;
import org.springframework.data.jpa.repository.JpaRepository;

/** 차수 반환 기록 저장소. 조회·추가만. */
public interface StageReturnRepository extends JpaRepository<StageReturn, Long> {

    List<StageReturn> findBySecurityRequestIdOrderByReturnedAtDescIdDesc(Long securityRequestId);
}
