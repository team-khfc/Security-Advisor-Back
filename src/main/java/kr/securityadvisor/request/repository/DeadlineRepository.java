package kr.securityadvisor.request.repository;

import java.util.List;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.request.domain.Deadline;
import kr.securityadvisor.request.domain.DeadlineKind;
import org.springframework.data.jpa.repository.JpaRepository;

/** 처리 기한 저장소. 기한일 수정은 없다 — 종료 + 새 행만. */
public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    List<Deadline> findBySecurityRequestIdOrderByStartedAtAscIdAsc(Long securityRequestId);

    List<Deadline> findBySecurityRequestIdAndKindAndActiveTrue(Long securityRequestId, DeadlineKind kind);

    List<Deadline> findByActiveTrue();

    List<Deadline> findBySecurityRequestIdAndKindAndFieldAndPausedTrueOrderByEndedAtDescIdDesc(
            Long securityRequestId, DeadlineKind kind, ReviewField field);
}
