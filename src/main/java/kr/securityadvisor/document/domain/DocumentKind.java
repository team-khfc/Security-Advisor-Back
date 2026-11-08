package kr.securityadvisor.document.domain;

/** 작성 초안 ≠ 제출본 ≠ 확정본(원칙 4). */
public enum DocumentKind {
    DRAFT("초안"),
    SUBMITTED("제출본"),
    CONFIRMED("확정본");

    private final String label;

    DocumentKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
