package kr.securityadvisor.llm.repository;

import java.util.List;
import kr.securityadvisor.llm.domain.AnalysisJob;
import kr.securityadvisor.llm.domain.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/** 분석 작업 저장소. */
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select j from AnalysisJob j where j.id = :id")
    java.util.Optional<AnalysisJob> lockById(Long id);

    List<AnalysisJob> findTop20ByStatusOrderByIdAsc(AnalysisStatus status);

    List<AnalysisJob> findTop20ByStatusAndStartedAtBeforeOrderByIdAsc(AnalysisStatus status, java.time.Instant before);

    java.util.Optional<AnalysisJob> findFirstByBusinessCaseIdAndUploadedFileIdAndStatusInOrderByIdDesc(
            Long businessCaseId, Long uploadedFileId, List<AnalysisStatus> statuses);

    List<AnalysisJob> findByBusinessCaseIdOrderByIdDesc(Long businessCaseId);

    List<AnalysisJob> findByBusinessCaseIdAndStatusOrderByIdDesc(Long businessCaseId, AnalysisStatus status);
}
