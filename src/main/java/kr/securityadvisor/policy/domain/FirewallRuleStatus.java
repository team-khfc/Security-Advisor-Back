package kr.securityadvisor.policy.domain;

/** 저장 상태. 만료 예정·만료는 조회 시 계산(FirewallRuleView). */
public enum FirewallRuleStatus {
    PENDING_APPLY("적용 대기"),
    APPLIED("적용 완료"),
    NOT_APPLICABLE("적용 불가"),
    REVOKED("회수");

    private final String label;

    FirewallRuleStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
