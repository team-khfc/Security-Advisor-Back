package kr.securityadvisor.assignment.dto;

import java.time.Instant;
import kr.securityadvisor.assignment.domain.Assignment;
import kr.securityadvisor.common.schema.ReviewField;

/** 배정 응답. 비활성(재배정으로 내려간) 배정도 이력으로 함께 보인다. */
public record AssignmentResponse(
        Long assignmentId,
        Long securityRequestId,
        ReviewField field,
        String assigneeUsername,
        boolean active,
        String reason,
        String assignedBy,
        Instant assignedAt,
        Instant deactivatedAt) {

    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getSecurityRequestId(),
                assignment.getField(),
                assignment.getAssigneeUsername(),
                assignment.isActive(),
                assignment.getReason(),
                assignment.getAssignedBy(),
                assignment.getAssignedAt(),
                assignment.getDeactivatedAt());
    }
}
