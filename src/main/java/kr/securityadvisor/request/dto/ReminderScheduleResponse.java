package kr.securityadvisor.request.dto;

import java.time.Instant;
import java.time.ZonedDateTime;
import kr.securityadvisor.request.domain.ReminderSchedule;

/**
 * 기한 알림 스케줄.
 *
 * @param nextRunAt 다음 실행 예정 시각(꺼져 있으면 null)
 */
public record ReminderScheduleResponse(
        String name,
        String cron,
        String timezone,
        boolean enabled,
        ZonedDateTime nextRunAt,
        String updatedBy,
        Instant updatedAt) {

    public static ReminderScheduleResponse from(ReminderSchedule schedule, ZonedDateTime nextRunAt, String timezone) {
        return new ReminderScheduleResponse(
                schedule.getName(),
                schedule.getCronExpression(),
                timezone,
                schedule.isEnabled(),
                nextRunAt,
                schedule.getUpdatedBy(),
                schedule.getUpdatedAt());
    }
}
