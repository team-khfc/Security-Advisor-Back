package kr.securityadvisor.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 기한 알림 스케줄 변경(기관 관리자).
 *
 * @param cron Spring 6필드 cron(초 분 시 일 월 요일), Asia/Seoul 기준. 예: "0 10 8 * * *"(매일 08:10), "0 0 9 * * 1-5"(평일 09:00)
 * @param enabled false 면 자동 실행을 멈춘다(관리자 수동 실행은 가능)
 */
public record ReminderScheduleCommand(
        @NotBlank @Size(max = 100) String cron, boolean enabled) {}
