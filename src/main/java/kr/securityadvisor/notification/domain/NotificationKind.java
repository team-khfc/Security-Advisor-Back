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
    REVIEW_RECHECK
}
