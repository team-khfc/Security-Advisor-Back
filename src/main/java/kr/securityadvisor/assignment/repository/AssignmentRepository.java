package kr.securityadvisor.assignment.repository;

import java.util.List;
import java.util.Optional;
import kr.securityadvisor.assignment.domain.Assignment;
import kr.securityadvisor.common.schema.ReviewField;
import org.springframework.data.jpa.repository.JpaRepository;

/** 배정 저장소. */
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findBySecurityRequestIdOrderByAssignedAtAscIdAsc(Long securityRequestId);

    Optional<Assignment> findBySecurityRequestIdAndFieldAndActiveTrue(Long securityRequestId, ReviewField field);

    List<Assignment> findBySecurityRequestIdInAndFieldAndActiveTrue(List<Long> securityRequestIds, ReviewField field);

    List<Assignment> findByAssigneeUsernameAndActiveTrueOrderByAssignedAtDesc(String assigneeUsername);

    List<Assignment> findByAssigneeUsernameAndFieldAndActiveTrue(String assigneeUsername, ReviewField field);
}
