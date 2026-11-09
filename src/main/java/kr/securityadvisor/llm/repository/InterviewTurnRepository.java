package kr.securityadvisor.llm.repository;

import java.util.List;
import kr.securityadvisor.llm.domain.InterviewTurn;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사업 건 소유자의 최근 인터뷰만 제한된 개수로 조회한다. */
public interface InterviewTurnRepository extends JpaRepository<InterviewTurn, Long> {
    List<InterviewTurn> findTop30ByBusinessCaseIdAndApplicantUsernameOrderByIdDesc(Long caseId, String username);
}
