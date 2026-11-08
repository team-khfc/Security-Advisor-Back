package kr.securityadvisor.policy.repository;

import java.util.List;
import kr.securityadvisor.policy.domain.FirewallRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 방화벽 규칙 저장소. 삭제하지 않는다(회수로 표현). */
public interface FirewallRuleRepository extends JpaRepository<FirewallRule, Long> {

    List<FirewallRule> findBySecurityRequestIdOrderByRuleNoAsc(Long securityRequestId);

    List<FirewallRule> findAllByOrderByIdDesc();

    boolean existsBySubmissionId(Long submissionId);

    @Query("select r from FirewallRule r where r.securityRequestId in "
            + "(select q.id from SecurityRequest q where q.businessCaseId in "
            + "(select c.id from BusinessCase c where c.applicantUsername = :applicant)) order by r.id desc")
    List<FirewallRule> findAllForApplicant(@Param("applicant") String applicant);
}
