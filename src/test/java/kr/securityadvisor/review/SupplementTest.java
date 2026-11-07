package kr.securityadvisor.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.review.domain.Supplement;
import kr.securityadvisor.review.domain.SupplementStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 보완 요청: 사유·대상 항목·담당자 필수, OPEN → ANSWERED → RESOLVED. */
class SupplementTest {

    private static final Instant NOW = Instant.parse("2026-11-07T09:00:00Z");

    @Test
    @DisplayName("사유나 대상 항목이 없으면 보완 요청을 만들 수 없다")
    void requiresReasonAndTargets() {
        assertThatThrownBy(() -> Supplement.request(1L, 2L, ReviewField.INFRA, "[\"a\"]", true, " ", "infra", NOW))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> Supplement.request(1L, 2L, ReviewField.INFRA, "[]", false, "사유", "infra", NOW))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("OPEN → 답변(ANSWERED) → 담당자 해결(RESOLVED), 답변 없이 해결 불가·중복 답변 불가")
    void lifecycle() {
        Supplement supplement = Supplement.request(
                1L, 2L, ReviewField.SECURITY, "[\"review.logging\"]", true, "보관 기간 누락", "security", NOW);
        assertThat(supplement.getStatus()).isEqualTo(SupplementStatus.OPEN);
        assertThat(supplement.isOpen()).isTrue();
        assertThatThrownBy(() -> supplement.resolve("security", NOW)).isInstanceOf(DomainRuleViolationException.class);

        supplement.answer("1년 보관으로 수정", NOW);
        assertThat(supplement.getStatus()).isEqualTo(SupplementStatus.ANSWERED);
        assertThat(supplement.isOpen()).isFalse();
        assertThatThrownBy(() -> supplement.answer("다시", NOW)).isInstanceOf(DomainRuleViolationException.class);

        supplement.resolve("security", NOW);
        assertThat(supplement.getStatus()).isEqualTo(SupplementStatus.RESOLVED);
        assertThat(supplement.getResolvedBy()).isEqualTo("security");
    }
}
