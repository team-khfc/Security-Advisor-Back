package kr.securityadvisor.insight.dto;

import java.util.Map;

/**
 * 업무 지표(F-16) — 읽기 전용 계산값. 담당자 개인 순위를 제공하지 않는다(불변식 29).
 *
 * @param requestsByStage 단계별 진행 중 신청 수(종료 상태 제외)
 * @param requestsByStatus 상태별 신청 수(전체)
 * @param activeDeadlines 활성 기한의 상태별 수(ON_TRACK·IMMINENT·OVERDUE)
 * @param supplements 보완 요청 상태별 수
 * @param firewallRules 방화벽 규칙 계산 상태별 수
 * @param averageReviewDays 협의 요청(2단계 진입)부터 3단계 전이까지 평균 일수(완료 건 기준, 없으면 null)
 * @param completedReviewCount 평균 산정에 쓰인 건수
 */
public record InsightSummary(
        Map<String, Long> requestsByStage,
        Map<String, Long> requestsByStatus,
        Map<String, Long> activeDeadlines,
        Map<String, Long> supplements,
        Map<String, Long> firewallRules,
        Double averageReviewDays,
        long completedReviewCount) {}
