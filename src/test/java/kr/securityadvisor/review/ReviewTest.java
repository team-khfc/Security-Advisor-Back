package kr.securityadvisor.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.review.domain.Review;
import kr.securityadvisor.review.domain.ReviewChecklist;
import kr.securityadvisor.review.domain.ReviewStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 협의 애그리거트 불변식: INFRA 선행, 할당 완료 ≠ 협의 완료, 체크리스트·의견 필수, 재검토는 값을 보존한다. */
class ReviewTest {

    private static final Instant NOW = Instant.parse("2026-11-07T09:00:00Z");

    @Test
    @DisplayName("INFRA 할당 완료는 협의 완료가 아니다(상태 ALLOCATED, completed 아님)")
    void allocationIsNotCompletion() {
        Review infra = Review.open(1L, ReviewField.INFRA);
        infra.completeAllocation("infra", NOW);
        assertThat(infra.getStatus()).isEqualTo(ReviewStatus.ALLOCATED);
        assertThat(infra.isAllocated()).isTrue();
        assertThat(infra.isCompleted()).isFalse();
        assertThat(infra.getAllocatedBy()).isEqualTo("infra");
    }

    @Test
    @DisplayName("할당 완료는 INFRA 분야에서만 할 수 있다")
    void onlyInfraCanAllocate() {
        Review security = Review.open(1L, ReviewField.SECURITY);
        assertThatThrownBy(() -> security.completeAllocation("security", NOW))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("SECURITY 협의는 INFRA 할당 완료 전에는 완료할 수 없다(INFRA 선행)")
    void securityRequiresInfraAllocation() {
        Review security = Review.open(1L, ReviewField.SECURITY);
        Map<String, Boolean> all = allChecked(ReviewField.SECURITY);
        assertThatThrownBy(() -> security.complete("security", "이상 없음", all, "{}", false, 0, NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("INFRA");
        security.complete("security", "이상 없음", all, "{}", true, 0, NOW);
        assertThat(security.isCompleted()).isTrue();
        assertThat(security.getCompletedBy()).isEqualTo("security");
    }

    @Test
    @DisplayName("체크리스트가 하나라도 빠지면, 의견이 비면, 미해결 보완이 있으면 완료할 수 없다")
    void completionPreconditions() {
        Review infra = Review.open(1L, ReviewField.INFRA);
        Map<String, Boolean> partial = allChecked(ReviewField.INFRA);
        partial.put("port", false);
        assertThatThrownBy(() -> infra.complete("infra", "확인", partial, "{}", true, 0, NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("port");
        Map<String, Boolean> all = allChecked(ReviewField.INFRA);
        assertThatThrownBy(() -> infra.complete("infra", " ", all, "{}", true, 0, NOW))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> infra.complete("infra", "확인", all, "{}", true, 1, NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("보완");
        infra.complete("infra", "확인", all, "{}", true, 0, NOW);
        assertThatThrownBy(() -> infra.complete("infra", "확인", all, "{}", true, 0, NOW))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("보완 요청 → 답변 → 원래 상태(할당 여부 유지), 완료 후 재검토는 의견·할당을 보존하고 완료 표시만 지운다")
    void supplementAndRecheck() {
        Review infra = Review.open(1L, ReviewField.INFRA);
        infra.completeAllocation("infra", NOW);
        infra.requestSupplement();
        assertThat(infra.getStatus()).isEqualTo(ReviewStatus.SUPPLEMENT_REQUESTED);
        infra.onSupplementAnswered();
        assertThat(infra.getStatus()).isEqualTo(ReviewStatus.ALLOCATED);

        infra.complete("infra", "할당 완료", allChecked(ReviewField.INFRA), "{}", true, 0, NOW);
        assertThatThrownBy(infra::requestSupplement).isInstanceOf(DomainRuleViolationException.class);
        infra.markRecheck();
        assertThat(infra.getStatus()).isEqualTo(ReviewStatus.RECHECK);
        assertThat(infra.isCompleted()).isFalse();
        assertThat(infra.getOpinion()).isEqualTo("할당 완료");
        assertThat(infra.isAllocated()).isTrue();
    }

    private static Map<String, Boolean> allChecked(ReviewField field) {
        Map<String, Boolean> map = new HashMap<>();
        ReviewChecklist.itemsOf(field).forEach(item -> map.put(item, true));
        return map;
    }
}
