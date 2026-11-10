package kr.securityadvisor.insight.dto;

import java.util.Map;

/**
 * 업무 지표(F-16) — 읽기 전용 계산값. 감사 이력·업무 상태에서 조회 시점에 계산하고 저장하지 않는다. 담당자 개인 순위를 제공하지
 * 않는다(불변식 29). 비율은 0~1 이며 분모가 0 이면 null. 시연 수치는 합성 데이터 기준이다.
 *
 * @param requestsByDocType 서류 유형별 신청 수(전체)
 * @param requestsByStage 단계별 진행 중 신청 수(종료 상태 제외)
 * @param requestsByStatus 상태별 신청 수(전체)
 * @param averageBusinessDaysByStage 단계별 평균 소요 영업일(1: 생성→협의 요청, 2: 협의 요청→3단계, 3: 3단계→제출, 4: 제출→확정). 완료 건 기준
 * @param completedByStage 단계별 평균 산정에 쓰인 건수
 * @param activeDeadlines 활성 기한의 상태별 수(ON_TRACK·IMMINENT·OVERDUE)
 * @param overdueDeadlines 기한 경과 건수(활성 기한 중 OVERDUE)
 * @param supplements 보완 요청 상태별 수
 * @param supplementsPerRequest 신청당 보완 요청 횟수(협의를 요청한 신청 기준)
 * @param stageReturnRate 차수 반환률(협의를 요청한 신청 중 한 번이라도 반환된 비율)
 * @param firstPassRate 1회 통과율(확정된 신청 중 보완·반환 없이 확정된 비율)
 * @param aiProposals AI 제안 수(항목 단위, 감사 이력 기준)
 * @param aiProposalsConfirmedUnchanged AI 값이 수정 없이 사용자·담당자 확인된 수
 * @param aiProposalsModified AI 값이 확인 전에 사용자 답변으로 바뀐 수
 * @param aiProposalConfirmRate AI 제안 확인율(확인 또는 수정으로 결론난 제안 중 수정 없이 확인된 비율)
 * @param firewallRules 방화벽 규칙 계산 상태별 수
 * @param averageReviewDays 협의 요청(2단계 진입)부터 3단계 전이까지 평균 달력일(완료 건 기준, 없으면 null)
 * @param completedReviewCount 평균 협의 일수 산정에 쓰인 건수
 */
public record InsightSummary(
        Map<String, Long> requestsByDocType,
        Map<String, Long> requestsByStage,
        Map<String, Long> requestsByStatus,
        Map<String, Double> averageBusinessDaysByStage,
        Map<String, Long> completedByStage,
        Map<String, Long> activeDeadlines,
        long overdueDeadlines,
        Map<String, Long> supplements,
        Double supplementsPerRequest,
        Double stageReturnRate,
        Double firstPassRate,
        long aiProposals,
        long aiProposalsConfirmedUnchanged,
        long aiProposalsModified,
        Double aiProposalConfirmRate,
        Map<String, Long> firewallRules,
        Double averageReviewDays,
        long completedReviewCount,
        java.time.Instant calculatedAt) {}
