package kr.securityadvisor.request.service;

import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.request.domain.ReminderSchedule;
import kr.securityadvisor.request.dto.ReminderScheduleCommand;
import kr.securityadvisor.request.dto.ReminderScheduleResponse;
import kr.securityadvisor.request.repository.ReminderScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 기한 알림 스케줄 조회·변경(기관 관리자). 변경은 커밋된 뒤 스케줄러에 바로 반영된다 — 재시작 불필요. */
@Service
@RequiredArgsConstructor
public class DeadlineReminderScheduleService {

    private final ReminderScheduleRepository reminderScheduleRepository;
    private final DeadlineReminderScheduler scheduler;
    private final AuditRecorder auditRecorder;

    @Transactional(readOnly = true)
    public ReminderScheduleResponse get(MemberPrincipal admin) {
        admin.requireAnyRole(MemberRole.ADMIN);
        return toResponse(requireSchedule());
    }

    /** 멱등: 같은 값이면 그대로 돌려준다. cron 형식 오류는 422. */
    @Transactional
    public ReminderScheduleResponse change(MemberPrincipal admin, ReminderScheduleCommand command) {
        admin.requireAnyRole(MemberRole.ADMIN);
        ReminderSchedule schedule = requireSchedule();
        String before = schedule.getCronExpression() + (schedule.isEnabled() ? " 사용" : " 중지");
        schedule.change(command.cron(), command.enabled(), admin.username());
        String after = schedule.getCronExpression() + (schedule.isEnabled() ? " 사용" : " 중지");
        if (!before.equals(after)) {
            auditRecorder.record(
                    admin,
                    AuditTargetType.REMINDER_SCHEDULE,
                    schedule.getName(),
                    null,
                    AuditAction.REMINDER_SCHEDULE_CHANGED,
                    null,
                    "기한 알림 스케줄 " + before + " → " + after);
        }
        // 커밋된 값만 스케줄러에 반영한다(롤백되면 이전 등록 유지)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                scheduler.apply(schedule);
            }
        });
        return toResponse(schedule);
    }

    private ReminderSchedule requireSchedule() {
        return reminderScheduleRepository
                .findByName(ReminderSchedule.DEADLINE)
                .orElseThrow(() -> new NotFoundException("기한 알림 스케줄이 없습니다"));
    }

    private ReminderScheduleResponse toResponse(ReminderSchedule schedule) {
        return ReminderScheduleResponse.from(
                schedule, scheduler.nextRun(schedule).orElse(null), DeadlineReminderScheduler.KOREA.getId());
    }
}
