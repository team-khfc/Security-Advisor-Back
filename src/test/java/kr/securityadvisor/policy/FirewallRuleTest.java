package kr.securityadvisor.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.policy.domain.FirewallRule;
import kr.securityadvisor.policy.domain.FirewallRuleStatus;
import kr.securityadvisor.policy.domain.RulePeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 방화벽 규칙 불변식 26~28: 값 복사·수정 없음, 전이는 사람 기록·사유 필수, 종료일 없으면 재검토일. */
class FirewallRuleTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");

    @Test
    @DisplayName("종료일도 재검토일도 없으면 만들 수 없고, 상시 규칙은 파싱 시 재검토일(시작+12개월)이 붙는다")
    void endOrReviewRequired() {
        assertThatThrownBy(() -> rule(null, null))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("재검토일");
        RulePeriod open = RulePeriod.parse("2026-12-01~");
        assertThat(open.end()).isNull();
        assertThat(open.reviewDate()).isEqualTo(LocalDate.of(2027, 12, 1));
        RulePeriod closed = RulePeriod.parse("2026-12-01~2027-11-30");
        assertThat(closed.reviewDate()).isNull();
        assertThatThrownBy(() -> RulePeriod.parse("언제까지나")).isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("적용 대기 → 적용 완료 → 회수(사유 필수). 적용 불가도 사유 필수, 완료된 규칙은 다시 적용 불가로 못 바꾼다")
    void lifecycleByHumanRecord() {
        FirewallRule rule = rule(LocalDate.of(2027, 11, 30), null);
        assertThat(rule.getStatus()).isEqualTo(FirewallRuleStatus.PENDING_APPLY);
        assertThatThrownBy(() -> rule.revoke("infra", "사유", NOW)).isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> rule.recordNotApplicable("infra", " ", NOW))
                .isInstanceOf(DomainRuleViolationException.class);
        rule.recordApplied("infra", "장비 반영 완료(합성)", NOW);
        assertThat(rule.getStatus()).isEqualTo(FirewallRuleStatus.APPLIED);
        assertThat(rule.getAppliedBy()).isEqualTo("infra");
        assertThatThrownBy(() -> rule.recordNotApplicable("infra", "사유", NOW))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> rule.revoke("infra", "", NOW)).isInstanceOf(DomainRuleViolationException.class);
        rule.revoke("infra", "서비스 종료", NOW);
        assertThat(rule.getStatus()).isEqualTo(FirewallRuleStatus.REVOKED);
        assertThat(rule.getReason()).isEqualTo("서비스 종료");
    }

    private static FirewallRule rule(LocalDate end, LocalDate review) {
        return FirewallRule.fromConfirmedSubmission(
                1L, 2L, 1, "10.0.0.0/24 (합성)", "10.0.1.10 (합성)", "TCP", "443", LocalDate.of(2026, 12, 1), end, review);
    }
}
