package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import kr.securityadvisor.common.calendar.BusinessCalendar;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.request.domain.Deadline;
import kr.securityadvisor.request.domain.DeadlineKind;
import kr.securityadvisor.request.domain.DeadlineStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 처리 기한의 저장형 규칙(불변식 22·23·31): 기한일은 저장값, 임박·지남은 계산값, 종료는 행 보존. */
class DeadlineTest {

    private static final BusinessCalendar CALENDAR = new BusinessCalendar(Set.of());
    private static final Instant NOW = Instant.parse("2026-11-06T03:00:00Z");
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 6);

    @Test
    @DisplayName("협의 기한은 분야가 필수이고 기산일 기준 영업일로 기한일을 정한다")
    void reviewDeadline() {
        Deadline deadline = Deadline.start(1L, ReviewField.INFRA, DeadlineKind.REVIEW, NOW, FRIDAY, 5, CALENDAR, 1);

        assertThat(deadline.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(deadline.isActive()).isTrue();
        assertThatThrownBy(() -> Deadline.start(1L, null, DeadlineKind.REVIEW, NOW, FRIDAY, 5, CALENDAR, 1))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> Deadline.start(1L, ReviewField.INFRA, DeadlineKind.SUPPLEMENT_RESPONSE, NOW, FRIDAY, 5, CALENDAR, 1))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("상태는 오늘 기준 계산: 기한 내 → 임박(남은 영업일 1 이하) → 지남(기한일 다음 날부터)")
    void statusIsComputed() {
        Deadline deadline = Deadline.start(1L, ReviewField.INFRA, DeadlineKind.REVIEW, NOW, FRIDAY, 5, CALENDAR, 1);

        assertThat(deadline.statusOn(LocalDate.of(2026, 11, 9), CALENDAR, 1)).isEqualTo(DeadlineStatus.ON_TRACK);
        assertThat(deadline.statusOn(LocalDate.of(2026, 11, 12), CALENDAR, 1)).isEqualTo(DeadlineStatus.IMMINENT);
        assertThat(deadline.statusOn(LocalDate.of(2026, 11, 13), CALENDAR, 1)).isEqualTo(DeadlineStatus.IMMINENT);
        assertThat(deadline.statusOn(LocalDate.of(2026, 11, 14), CALENDAR, 1)).isEqualTo(DeadlineStatus.OVERDUE);
        // 지나도 기한일·활성 여부는 그대로(상태 전이 없음)
        assertThat(deadline.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(deadline.isActive()).isTrue();
    }

    @Test
    @DisplayName("종료는 기한일을 바꾸지 않고 종료 시각만 남기며, 두 번 종료할 수 없다")
    void endKeepsDueDate() {
        Deadline deadline = Deadline.start(1L, null, DeadlineKind.FINAL_CONFIRMATION, NOW, FRIDAY, 5, CALENDAR, 1);

        deadline.end(NOW.plusSeconds(60));

        assertThat(deadline.isActive()).isFalse();
        assertThat(deadline.getEndedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(deadline.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(deadline.statusOn(LocalDate.of(2026, 12, 1), CALENDAR, 1)).isEqualTo(DeadlineStatus.ENDED);
        assertThatThrownBy(() -> deadline.end(NOW)).isInstanceOf(DomainRuleViolationException.class);
    }
}
