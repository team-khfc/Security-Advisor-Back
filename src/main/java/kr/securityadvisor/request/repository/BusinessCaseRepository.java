package kr.securityadvisor.request.repository;

import java.util.List;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.request.domain.BusinessCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 사업 건 저장소(애그리거트 루트). */
public interface BusinessCaseRepository extends JpaRepository<BusinessCase, Long> {

    Page<BusinessCase> findByApplicantUsername(String applicantUsername, Pageable pageable);

    List<BusinessCase> findByApplicantUsernameOrderByIdAsc(String applicantUsername);

    /** 상세 접근과 동일한 소유·분야별 활성 배정 범위에서만 페이지를 계산한다. */
    @Query("select c from BusinessCase c where :role = 'ADMIN' "
            + "or (:role = 'APPLICANT' and c.applicantUsername = :username) "
            + "or (:role in ('INFRA','SECURITY') and exists (select r.id from SecurityRequest r, Assignment a "
            + "where r.businessCaseId = c.id and a.securityRequestId = r.id "
            + "and a.active = true and a.field = :field and a.assigneeUsername = :username))")
    Page<BusinessCase> findVisible(
            @Param("username") String username,
            @Param("role") String role,
            @Param("field") ReviewField field,
            Pageable pageable);
}
