package kr.securityadvisor.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.review.domain.FinalConfirmation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 최종 확인: INFRA 선행, 분야당 1건, 확인자 필수. */
class FinalConfirmationTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");

    @Test
    @DisplayName("SECURITY 최종 확인은 INFRA 확인이 먼저 있어야 한다")
    void securityRequiresInfraFirst() {
        assertThatThrownBy(() ->
                        FinalConfirmation.confirm(1L, 2L, ReviewField.SECURITY, null, "security", false, false, NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("INFRA");
        FinalConfirmation ok =
                FinalConfirmation.confirm(1L, 2L, ReviewField.SECURITY, " 이상 없음 ", "security", true, false, NOW);
        assertThat(ok.getOpinion()).isEqualTo("이상 없음");
        assertThat(ok.getConfirmedBy()).isEqualTo("security");
    }

    @Test
    @DisplayName("같은 제출본·같은 분야 확인은 한 번만, 확인자 없이는 불가")
    void onePerFieldAndActorRequired() {
        assertThatThrownBy(() -> FinalConfirmation.confirm(1L, 2L, ReviewField.INFRA, null, "infra", false, true, NOW))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> FinalConfirmation.confirm(1L, 2L, ReviewField.INFRA, null, " ", false, false, NOW))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThat(FinalConfirmation.confirm(1L, 2L, ReviewField.INFRA, null, "infra", false, false, NOW)
                        .getField())
                .isEqualTo(ReviewField.INFRA);
    }
}
