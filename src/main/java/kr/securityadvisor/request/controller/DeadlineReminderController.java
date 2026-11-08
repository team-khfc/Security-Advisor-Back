package kr.securityadvisor.request.controller;

import jakarta.validation.Valid;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.DeadlineReminderRunCommand;
import kr.securityadvisor.request.dto.DeadlineReminderRunResult;
import kr.securityadvisor.request.dto.ReminderScheduleCommand;
import kr.securityadvisor.request.dto.ReminderScheduleResponse;
import kr.securityadvisor.request.service.DeadlineReminderScheduleService;
import kr.securityadvisor.request.service.DeadlineReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기한 알림 API(기관 관리자). 스케줄은 단일 자원(GET/PUT /api/deadline-reminder-schedule)으로, 수동 실행은 "실행 기록" 자원 생성
 * (POST /api/deadline-reminder-runs)으로 표현한다.
 */
@RestController
@RequiredArgsConstructor
public class DeadlineReminderController {

    private final DeadlineReminderService deadlineReminderService;
    private final DeadlineReminderScheduleService scheduleService;

    /** 현재 스케줄(cron·사용 여부·다음 실행 시각). */
    @GetMapping("/api/deadline-reminder-schedule")
    public ReminderScheduleResponse schedule(@CurrentMember MemberPrincipal admin) {
        return scheduleService.get(admin);
    }

    /** 스케줄 변경 — 저장 즉시 반영(재시작 불필요). cron 형식 오류 422. */
    @PutMapping("/api/deadline-reminder-schedule")
    public ReminderScheduleResponse changeSchedule(
            @CurrentMember MemberPrincipal admin, @Valid @RequestBody ReminderScheduleCommand command) {
        return scheduleService.change(admin, command);
    }

    @PostMapping("/api/deadline-reminder-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public DeadlineReminderRunResult run(
            @CurrentMember MemberPrincipal admin, @RequestBody(required = false) DeadlineReminderRunCommand command) {
        return deadlineReminderService.runAs(admin, command);
    }
}
