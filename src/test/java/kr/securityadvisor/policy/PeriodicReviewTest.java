package kr.securityadvisor.policy;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.FirewallPolicy;
import kr.securityadvisor.policy.domain.*;
import org.junit.jupiter.api.Test;

class PeriodicReviewTest {
    private FirewallRule rule() {
        var rule = FirewallRule.fromConfirmedSubmission(
                1L,
                2L,
                1,
                "192.0.2.1",
                "198.51.100.1",
                "TCP",
                "443",
                LocalDate.of(2024, 2, 29),
                null,
                LocalDate.of(2025, 2, 28));
        rule.initializeReviewPolicy(new FirewallPolicy(12, 10));
        return rule;
    }

    @Test
    void 윤년과_임박_당일_경과_경계는_같은_판정이며_기록없이_연장하지_않는다() {
        var rule = rule();
        assertThat(rule.needsReview(LocalDate.of(2026, 1, 1))).isFalse();
        rule.recordApplied("synthetic-infra", "합성 적용", Instant.EPOCH);
        assertThat(rule.getReviewDate()).isEqualTo("2025-02-28");
        assertThat(rule.reviewStatus(LocalDate.of(2025, 2, 17))).isEqualTo("SCHEDULED");
        assertThat(rule.reviewStatus(LocalDate.of(2025, 2, 18))).isEqualTo("DUE");
        assertThat(rule.reviewStatus(LocalDate.of(2025, 2, 28))).isEqualTo("DUE");
        assertThat(rule.reviewStatus(LocalDate.of(2025, 3, 1))).isEqualTo("OVERDUE");
        assertThat(rule.getReviewDate()).isEqualTo("2025-02-28");
    }

    @Test
    void 판단과_사유를_보존하고_유지시만_다음주기를_정하며_회수후_대상에서_제외한다() {
        var rule = rule();
        var today = LocalDate.of(2026, 11, 13);
        assertThatThrownBy(() -> rule.review(PeriodicReviewDecision.KEEP, "합성", "infra", Instant.EPOCH, today))
                .isInstanceOf(DomainRuleViolationException.class);
        rule.recordApplied("infra", "합성", Instant.EPOCH);
        assertThatThrownBy(() -> rule.review(PeriodicReviewDecision.KEEP, " ", "infra", Instant.EPOCH, today))
                .isInstanceOf(DomainRuleViolationException.class);
        rule.review(PeriodicReviewDecision.CHANGE_REQUIRED, "새 신청 필요", "infra", Instant.EPOCH, today);
        assertThat(rule.reviewStatus(today)).isEqualTo("CHANGE_REQUIRED");
        assertThat(rule.getReviewDate()).isEqualTo("2025-02-28");
        rule.review(PeriodicReviewDecision.KEEP, "필요성 재확인", "new-infra", Instant.EPOCH, today);
        assertThat(rule.getReviewDate()).isEqualTo("2027-11-13");
        assertThat(rule.getReviewHistory()).hasSize(2);
        assertThat(rule.getReviewHistory().getFirst().reviewedBy()).isEqualTo("infra");
        assertThat(rule.needsReview(today)).isFalse();
        rule.review(PeriodicReviewDecision.REVOKE_REQUIRED, "사용 종료", "new-infra", Instant.EPOCH, today);
        assertThat(rule.needsReview(today)).isTrue();
        assertThat(rule.getStatus()).isEqualTo(FirewallRuleStatus.APPLIED);
        rule.revoke("new-infra", "합성 회수 기록", Instant.EPOCH);
        assertThat(rule.needsReview(today)).isFalse();
        assertThat(rule.getPort()).isEqualTo("443");
    }
}
