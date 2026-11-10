package kr.securityadvisor.request.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ValueSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FinalDraftTest {
    @Test
    @DisplayName("AI 서술은 사람 수정 후에도 원래 출처를 보존하고 확인 전 제출을 막는다")
    void humanConfirmation() {
        FinalDraft draft = FinalDraft.propose(1L, "fingerprint", "AI 합성 초안", ValueSource.AI_INFERRED, "fake/offline");
        assertThatThrownBy(() -> draft.requireSubmittable("fingerprint"))
                .isInstanceOf(DomainRuleViolationException.class);
        draft.confirm("사용자가 수정한 합성 서술", "synthetic", Instant.now(), 0, "fingerprint");
        draft.requireSubmittable("fingerprint");
        assertThat(draft.getText()).isEqualTo("사용자가 수정한 합성 서술");
        assertThat(draft.getOriginalText()).isEqualTo("AI 합성 초안");
        assertThat(draft.getValueSource()).isEqualTo(ValueSource.AI_INFERRED);
        draft.invalidate();
        assertThatThrownBy(() -> draft.requireSubmittable("fingerprint"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("오래된 답변·다른 편집 버전·빈 서술은 확인할 수 없다")
    void rejectStale() {
        FinalDraft draft = FinalDraft.propose(1L, "old", "합성 서술", ValueSource.USER_ANSWER, null);
        assertThatThrownBy(() -> draft.confirm("변경", "synthetic", Instant.now(), 0, "new"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> draft.confirm("변경", "synthetic", Instant.now(), 9, "old"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> draft.confirm(" ", "synthetic", Instant.now(), 0, "old"))
                .isInstanceOf(DomainRuleViolationException.class);
    }
}
