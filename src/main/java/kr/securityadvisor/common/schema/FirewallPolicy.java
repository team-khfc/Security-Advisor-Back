package kr.securityadvisor.common.schema;

import tools.jackson.databind.annotation.JsonDeserialize;

/** 게시본에 고정되는 상시 규칙 재검토 주기와 달력일 알림 정책. */
public record FirewallPolicy(
        @JsonDeserialize(using = BusinessDaysDeserializer.class)
        Integer reviewMonths,

        @JsonDeserialize(using = BusinessDaysDeserializer.class)
        Integer reviewNoticeDays) {
    public FirewallPolicy {
        reviewMonths = reviewMonths == null ? 12 : reviewMonths;
        reviewNoticeDays = reviewNoticeDays == null ? 30 : reviewNoticeDays;
    }

    public static FirewallPolicy defaults() {
        return new FirewallPolicy(null, null);
    }
}
