package kr.securityadvisor.request.dto;

import java.time.LocalDate;

/**
 * 기한 알림 수동 실행(기관 관리자). 시연·검증용으로 기준일을 지정할 수 있다 — 비우면 오늘.
 *
 * @param asOf 기준일(선택)
 */
public record DeadlineReminderRunCommand(LocalDate asOf) {}
