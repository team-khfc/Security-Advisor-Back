package kr.securityadvisor.request.dto;

import java.time.LocalDate;

/**
 * 기한 알림 한 차례 실행 결과.
 *
 * @param asOf 기준일(Asia/Seoul)
 * @param scanned 검사한 활성 기한 수
 * @param imminent 게시본 임박 범위의 알림 대상 기한 수
 * @param overdue 경과 알림 대상 기한 수
 */
public record DeadlineReminderRunResult(LocalDate asOf, int scanned, int imminent, int overdue) {}
