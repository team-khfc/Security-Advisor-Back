package kr.securityadvisor.request.repository;

import java.util.List;
import kr.securityadvisor.request.domain.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;

/** 업로드 문서 저장소. */
public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

    List<UploadedFile> findByBusinessCaseIdOrderByIdAsc(Long businessCaseId);
}
