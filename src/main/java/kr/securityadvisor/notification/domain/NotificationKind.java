package kr.securityadvisor.notification.domain;

/** 알림 종류(수신자 관점). */
public enum NotificationKind {
    ASSIGNED,
    SUPPLEMENT_REQUESTED,
    SUPPLEMENT_ANSWERED,
    STAGE_RETURNED,
    SUBMITTED,
    FINAL_CONFIRMED,
    REQUEST_CONFIRMED,
    REQUEST_CLOSED,
    REVIEW_RECHECK,
    /** 기한 1영업일 전(F-13) */
    DEADLINE_IMMINENT,
    /** 기한 경과(F-13) */
    DEADLINE_OVERDUE,
    /** 자동 배정 실패 — 기관 관리자에게(F-17) */
    UNASSIGNED,
    /** 제출 뒤 공통 정보 변경 — 담당자에게(F-17) */
    COMMON_INFO_CHANGED
}
