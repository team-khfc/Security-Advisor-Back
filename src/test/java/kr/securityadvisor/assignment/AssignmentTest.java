package kr.securityadvisor.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import kr.securityadvisor.assignment.domain.Assignment;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ReviewField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 배정 규칙(불변식 20·21): 신청자 본인 배정 금지, 재배정은 사유 필수, 이전 배정은 비활성으로 보존. */
class AssignmentTest {

    private static final Instant NOW = Instant.parse("2026-11-07T09:00:00Z");

    @Test
    @DisplayName("자동 배정은 활성 상태로 만들어지고 배정자(assignedBy)가 없다")
    void autoAssign() {
        Assignment assignment = Assignment.autoAssign(1L, ReviewField.INFRA, "infra", "applicant", NOW);

        assertThat(assignment.isActive()).isTrue();
        assertThat(assignment.getAssignedBy()).isNull();
        assertThat(assignment.isAssignedTo("infra")).isTrue();
        assertThat(assignment.isAssignedTo("security")).isFalse();
    }

    @Test
    @DisplayName("신청자 본인은 담당자가 될 수 없다")
    void applicantCannotBeAssignee() {
        assertThatThrownBy(() -> Assignment.autoAssign(1L, ReviewField.INFRA, "applicant", "applicant", NOW))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(
                        () -> Assignment.reassign(1L, ReviewField.INFRA, "applicant", "applicant", "admin", "사유", NOW))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("재배정은 사유와 관리자가 필수이며, 이전 배정은 지우지 않고 비활성으로 남긴다")
    void reassignKeepsHistory() {
        Assignment previous = Assignment.autoAssign(1L, ReviewField.INFRA, "infra", "applicant", NOW);
        assertThatThrownBy(() -> Assignment.reassign(1L, ReviewField.INFRA, "infra2", "applicant", "admin", " ", NOW))
                .isInstanceOf(DomainRuleViolationException.class);

        previous.deactivate(NOW.plusSeconds(10));
        Assignment next =
                Assignment.reassign(1L, ReviewField.INFRA, "infra2", "applicant", "admin", "휴가", NOW.plusSeconds(10));

        assertThat(previous.isActive()).isFalse();
        assertThat(previous.getDeactivatedAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(previous.isAssignedTo("infra")).isFalse();
        assertThat(next.isActive()).isTrue();
        assertThat(next.getReason()).isEqualTo("휴가");
        assertThat(next.getAssignedBy()).isEqualTo("admin");
        assertThatThrownBy(() -> previous.deactivate(NOW)).isInstanceOf(DomainRuleViolationException.class);
    }
}
