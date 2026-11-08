package kr.securityadvisor.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 아침(설정 {@code securityadvisor.deadline-reminder.cron}, Asia/Seoul) 기한 알림을 실행한다. 실패는 로그만 — 업무에 영향 없음. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineReminderScheduler {

    private final DeadlineReminderService deadlineReminderService;

    @Scheduled(cron = "${securityadvisor.deadline-reminder.cron:-}", zone = "Asia/Seoul")
    public void runDaily() {
        try {
            deadlineReminderService.runToday();
        } catch (RuntimeException e) {
            log.warn("기한 알림 실행 실패: {}", e.getMessage(), e);
        }
    }
}
