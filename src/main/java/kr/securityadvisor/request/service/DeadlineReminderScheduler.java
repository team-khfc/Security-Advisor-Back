package kr.securityadvisor.request.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import kr.securityadvisor.request.domain.ReminderSchedule;
import kr.securityadvisor.request.repository.ReminderScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * 기한 알림 예약 실행. 스케줄(cron·사용 여부)은 DB(reminder_schedules)에 있고, 기동 시와 관리자가 바꿀 때마다 다시 등록한다 —
 * 재시작 없이 반영된다. 실행 실패는 로그만 남기고 업무에 영향을 주지 않는다. 시간대는 Asia/Seoul 고정.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineReminderScheduler {

    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final TaskScheduler taskScheduler;
    private final ReminderScheduleRepository reminderScheduleRepository;
    private final DeadlineReminderService deadlineReminderService;

    private ScheduledFuture<?> current;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reminderScheduleRepository
                .findByName(ReminderSchedule.DEADLINE)
                .ifPresentOrElse(
                        this::apply, () -> log.warn("기한 알림 스케줄(reminder_schedules.DEADLINE)이 없어 자동 실행을 등록하지 않습니다"));
    }

    /** 스케줄을 다시 등록한다(이전 등록은 취소). 꺼져 있으면 등록하지 않는다. */
    public synchronized void apply(ReminderSchedule schedule) {
        if (current != null) {
            current.cancel(false);
            current = null;
        }
        if (!schedule.isEnabled()) {
            log.info("기한 알림 자동 실행 꺼짐");
            return;
        }
        current = taskScheduler.schedule(this::runSafely, new CronTrigger(schedule.getCronExpression(), KOREA));
        log.info(
                "기한 알림 자동 실행 등록: cron '{}' ({}), 다음 실행 {}",
                schedule.getCronExpression(),
                KOREA,
                nextRun(schedule).orElse(null));
    }

    /** 다음 실행 예정 시각(꺼져 있거나 다음 시각이 없으면 비어 있음). */
    public Optional<ZonedDateTime> nextRun(ReminderSchedule schedule) {
        if (!schedule.isEnabled() || !CronExpression.isValidExpression(schedule.getCronExpression())) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                CronExpression.parse(schedule.getCronExpression()).next(ZonedDateTime.now(KOREA)));
    }

    void runSafely() {
        try {
            deadlineReminderService.runToday();
        } catch (RuntimeException e) {
            log.warn("기한 알림 실행 실패: {}", e.getMessage(), e);
        }
    }
}
