package kr.securityadvisor.request.domain;

/** 처리 기한 종류(F-13). REVIEW 는 분야별, 나머지는 신청 단위. */
public enum DeadlineKind {
    /** 분야별 협의 기한 — 배정(2단계 진입) 시각 기준 */
    REVIEW,
    /** 보완 응답 기한 — 보완 요청·차수 반환 시각 기준(신청자) */
    SUPPLEMENT_RESPONSE,
    /** 최종 확정 기한 — 제출 시각 기준 */
    FINAL_CONFIRMATION
}
