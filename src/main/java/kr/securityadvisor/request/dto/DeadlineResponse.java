package kr.securityadvisor.request.dto;

import java.time.Instant;
import java.time.LocalDate;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.request.domain.Deadline;
import kr.securityadvisor.request.domain.DeadlineKind;
import kr.securityadvisor.request.domain.DeadlineStatus;

/** 기한 응답. status·statusLabel 은 오늘(Asia/Seoul) 기준 계산값이다. */
public record DeadlineResponse(
        Long deadlineId,
        Long securityRequestId,
        ReviewField field,
        DeadlineKind kind,
        Instant startedAt,
        LocalDate dueDate,
        int policyVersion,
        boolean active,
        Instant endedAt,
        DeadlineStatus status,
        String statusLabel,
        boolean paused,
        Integer remainingBusinessDays) {

    public static DeadlineResponse from(Deadline deadline, DeadlineStatus status) {
        return new DeadlineResponse(
                deadline.getId(),
                deadline.getSecurityRequestId(),
                deadline.getField(),
                deadline.getKind(),
                deadline.getStartedAt(),
                deadline.getDueDate(),
                deadline.getPolicyVersion(),
                deadline.isActive(),
                deadline.getEndedAt(),
                status,
                status.label(),
                deadline.isPaused(),
                deadline.getRemainingBusinessDays());
    }
}
