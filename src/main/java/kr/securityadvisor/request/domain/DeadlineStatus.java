package kr.securityadvisor.request.domain;

/** 기한 상태 — 저장하지 않고 조회 시 계산한다(불변식 22·31). */
public enum DeadlineStatus {
    ON_TRACK("기한 내"),
    IMMINENT("기한 임박"),
    OVERDUE("기한 지남"),
    ENDED("종료");

    private final String label;

    DeadlineStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
