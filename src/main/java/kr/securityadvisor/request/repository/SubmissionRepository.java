package kr.securityadvisor.request.repository;

import java.util.List;
import java.util.Optional;
import kr.securityadvisor.request.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 제출본 저장소. 수정·삭제 메서드를 쓰지 않는다(불변식 4). */
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByIdempotencyKey(String idempotencyKey);

    List<Submission> findBySecurityRequestIdOrderBySequenceNoDesc(Long securityRequestId);

    int countBySecurityRequestId(Long securityRequestId);

    Optional<Submission> findFirstBySecurityRequestIdOrderBySequenceNoDesc(Long securityRequestId);

    /** 문서함(전체 — 담당자·관리자). */
    List<Submission> findAllByOrderBySubmittedAtDescIdDesc();

    /** 문서함(신청자 본인 — 자기 사업 건의 신청만). */
    @Query("select s from Submission s where s.securityRequestId in "
            + "(select r.id from SecurityRequest r where r.businessCaseId in "
            + "(select c.id from BusinessCase c where c.applicantUsername = :applicant)) "
            + "order by s.submittedAt desc, s.id desc")
    List<Submission> findAllForApplicant(@Param("applicant") String applicant);
}
