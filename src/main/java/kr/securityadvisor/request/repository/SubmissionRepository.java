package kr.securityadvisor.request.repository;

import java.util.List;
import java.util.Optional;
import kr.securityadvisor.request.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

/** 제출본 저장소. 수정·삭제 메서드를 쓰지 않는다(불변식 4). */
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByIdempotencyKey(String idempotencyKey);

    List<Submission> findBySecurityRequestIdOrderBySequenceNoDesc(Long securityRequestId);

    int countBySecurityRequestId(Long securityRequestId);
}
