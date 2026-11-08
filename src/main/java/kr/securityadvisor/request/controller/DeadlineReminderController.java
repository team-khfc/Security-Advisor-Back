package kr.securityadvisor.request.controller;

import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.DeadlineReminderRunCommand;
import kr.securityadvisor.request.dto.DeadlineReminderRunResult;
import kr.securityadvisor.request.service.DeadlineReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 기한 알림 수동 실행(기관 관리자). 스케줄러가 매일 하는 일을 "실행 기록" 자원 생성으로 표현한다 — 시연·검증용. */
@RestController
@RequiredArgsConstructor
public class DeadlineReminderController {

    private final DeadlineReminderService deadlineReminderService;

    @PostMapping("/api/deadline-reminder-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public DeadlineReminderRunResult run(
            @CurrentMember MemberPrincipal admin, @RequestBody(required = false) DeadlineReminderRunCommand command) {
        return deadlineReminderService.runAs(admin, command);
    }
}
