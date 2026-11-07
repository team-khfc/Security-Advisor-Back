package kr.securityadvisor.review.domain;

/** 보완 요청 상태. OPEN 이 하나라도 있으면 제출·협의 완료 불가(불변식 2). */
public enum SupplementStatus {
    OPEN("미해결"),
    ANSWERED("답변됨"),
    RESOLVED("해소");

    private final String label;

    SupplementStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
