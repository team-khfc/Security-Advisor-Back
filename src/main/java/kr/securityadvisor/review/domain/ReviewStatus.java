package kr.securityadvisor.review.domain;

/** 분야별 협의 상태(FUNCTIONAL_SPEC 6장). 할당 완료(ALLOCATED) ≠ 협의 완료(COMPLETED) — 불변식 12. */
public enum ReviewStatus {
    WAITING("검토 대기"),
    ALLOCATED("할당 완료"),
    IN_PROGRESS("검토 중"),
    SUPPLEMENT_REQUESTED("보완 요청"),
    COMPLETED("협의 완료"),
    RECHECK("재검토");

    private final String label;

    ReviewStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
