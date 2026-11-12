package kr.securityadvisor.policy.domain;

/** 정기 재검토 판단. 장비 변경이나 서류 변경을 자동 수행하지 않는다. */
public enum PeriodicReviewDecision {
    KEEP,
    CHANGE_REQUIRED,
    REVOKE_REQUIRED
}
