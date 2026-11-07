package kr.securityadvisor.request.repository;

import java.util.List;
import kr.securityadvisor.request.domain.SecurityRequest;
import org.springframework.data.jpa.repository.JpaRepository;

/** 신청 저장소(애그리거트 루트). */
public interface SecurityRequestRepository extends JpaRepository<SecurityRequest, Long> {

    List<SecurityRequest> findByBusinessCaseIdOrderByIdAsc(Long businessCaseId);
}
