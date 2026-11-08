package kr.securityadvisor.request.repository;

import java.util.List;
import kr.securityadvisor.request.domain.BusinessCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사업 건 저장소(애그리거트 루트). */
public interface BusinessCaseRepository extends JpaRepository<BusinessCase, Long> {

    Page<BusinessCase> findByApplicantUsername(String applicantUsername, Pageable pageable);

    List<BusinessCase> findByApplicantUsernameOrderByIdAsc(String applicantUsername);
}
