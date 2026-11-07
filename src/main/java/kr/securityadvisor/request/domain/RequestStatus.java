package kr.securityadvisor.request.domain;

import java.util.Set;

/**
 * 신청 상태(FUNCTIONAL_SPEC 6장 "신청 단계×상태"). 단계와 함께 저장하며 허용 조합만 쓴다(불변식 33).
 */
public enum RequestStatus {
    /** 1단계 작성 중 */
    DRAFTING("작성 중", Set.of(1), false),
    /** 2단계 협의 중 */
    IN_REVIEW("협의 중", Set.of(2), false),
    /** 2·3단계 보완 요청(신청자 답변 대기) */
    SUPPLEMENT_REQUESTED("보완 요청", Set.of(2, 3), false),
    /** 3단계 사용자 최종 검토 중 */
    FINAL_REVIEW("최종 검토 중", Set.of(3), false),
    /** 4단계 제출됨(담당자 최종 확인 대기) */
    SUBMITTED("제출됨", Set.of(4), false),
    /** 4단계 확정 — 종료 상태 */
    CONFIRMED("확정", Set.of(4), true),
    /** 담당자 종결(반려) — 모든 단계, 종료 상태 */
    CLOSED("종결", Set.of(1, 2, 3, 4), true),
    /** 신청자 철회 — 모든 단계, 종료 상태 */
    WITHDRAWN("철회", Set.of(1, 2, 3, 4), true);

    private final String label;
    private final Set<Integer> allowedStages;
    private final boolean terminal;

    RequestStatus(String label, Set<Integer> allowedStages, boolean terminal) {
        this.label = label;
        this.allowedStages = allowedStages;
        this.terminal = terminal;
    }

    public String label() {
        return label;
    }

    public boolean allowsStage(int stage) {
        return allowedStages.contains(stage);
    }

    /** 종결·철회·확정 이후에는 어떤 전이도 없다(불변식 35). */
    public boolean isTerminal() {
        return terminal;
    }

    /** 차수 반환 뒤 들어가는 그 단계의 첫 상태(6장: "3→1이면 1 작성 / 작성 중"). */
    public static RequestStatus firstOf(int stage) {
        return switch (stage) {
            case 1 -> DRAFTING;
            case 2 -> IN_REVIEW;
            case 3 -> FINAL_REVIEW;
            case 4 -> SUBMITTED;
            default -> throw new IllegalArgumentException("단계는 1~4 입니다: " + stage);
        };
    }
}
