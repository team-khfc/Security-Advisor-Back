package kr.securityadvisor.llm.repository;

import java.util.List;
import kr.securityadvisor.llm.domain.AnalysisJob;
import kr.securityadvisor.llm.domain.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/** 분석 작업 저장소. */
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

    List<AnalysisJob> findByBusinessCaseIdOrderByIdDesc(Long businessCaseId);

    List<AnalysisJob> findByBusinessCaseIdAndStatusOrderByIdDesc(Long businessCaseId, AnalysisStatus status);
}
