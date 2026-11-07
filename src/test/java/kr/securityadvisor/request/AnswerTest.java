package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.ValueSource;
import kr.securityadvisor.request.domain.Answer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 답변의 출처·확인 규칙(불변 원칙 1·2, 불변식 5)을 검증한다. */
class AnswerTest {

    private static final Instant NOW = Instant.parse("2026-11-07T09:00:00Z");

    @Test
    @DisplayName("사용자 답변은 USER_ANSWER 출처이며 확인된 값이다")
    void userAnswer() {
        Answer answer = Answer.userAnswer(AnswerScope.CASE, 1L, null, "project.name", " 민원 포털 ");

        assertThat(answer.getValue()).isEqualTo("민원 포털");
        assertThat(answer.getValueSource()).isEqualTo(ValueSource.USER_ANSWER);
        assertThat(answer.isConfirmed()).isTrue();
        assertThat(answer.isUnconfirmed()).isFalse();
    }

    @Test
    @DisplayName("AI 제안 값은 근거가 필수이고 확인 전까지 미확정이다")
    void aiInferred() {
        Answer answer = Answer.aiInferred(AnswerScope.CASE, 1L, null, "project.summary", "민원 접수 자동화", "사업계획서 2쪽");

        assertThat(answer.getValueSource()).isEqualTo(ValueSource.AI_INFERRED);
        assertThat(answer.isUnconfirmed()).isTrue();
        assertThatThrownBy(() -> Answer.aiInferred(AnswerScope.CASE, 1L, null, "k", "값", " "))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("사용자가 AI 제안을 확인하면 출처는 AI_INFERRED 로 남고 확인 표시가 붙는다")
    void confirmByUser() {
        Answer answer = Answer.aiInferred(AnswerScope.CASE, 1L, null, "project.summary", "민원 접수 자동화", "사업계획서 2쪽");

        answer.confirmByUser("applicant", NOW);

        assertThat(answer.isConfirmed()).isTrue();
        assertThat(answer.getConfirmedBy()).isEqualTo("applicant");
        assertThat(answer.getValueSource()).isEqualTo(ValueSource.AI_INFERRED);
        assertThat(answer.isUnconfirmed()).isFalse();
    }

    @Test
    @DisplayName("사용자 확인은 AI 제안 값에만 가능하다")
    void confirmByUserOnlyForAi() {
        Answer answer = Answer.userAnswer(AnswerScope.CASE, 1L, null, "project.name", "민원 포털");

        assertThatThrownBy(() -> answer.confirmByUser("applicant", NOW))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("확인된 값은 AI 제안으로 덮어쓸 수 없다(AI 는 제안만)")
    void aiCannotOverwriteConfirmed() {
        Answer answer = Answer.userAnswer(AnswerScope.CASE, 1L, null, "project.name", "민원 포털");

        assertThatThrownBy(() -> answer.proposeByAi("다른 값", "근거")).isInstanceOf(DomainRuleViolationException.class);
        assertThat(answer.getValue()).isEqualTo("민원 포털");
    }

    @Test
    @DisplayName("담당자 확인 필요 항목은 출처가 없고 미확정이며, 담당자 입력으로 채워진다")
    void staffCheckAndInput() {
        Answer answer = Answer.staffCheckNeeded(AnswerScope.CASE, 1L, null, "network.source");
        assertThat(answer.getValueSource()).isNull();
        assertThat(answer.isUnconfirmed()).isTrue();
        assertThat(answer.hasValue()).isFalse();

        answer.inputByStaff("infra", "10.0.0.0/24", NOW);

        assertThat(answer.getValueSource()).isEqualTo(ValueSource.STAFF_INPUT);
        assertThat(answer.getConfirmedBy()).isEqualTo("infra");
        assertThat(answer.isUnconfirmed()).isFalse();
    }

    @Test
    @DisplayName("STAFF_CONFIRMED 전이는 확인 주체가 있어야 하고 값이 있어야 한다(불변식 5)")
    void staffConfirmRequiresActorAndValue() {
        Answer answer = Answer.aiInferred(AnswerScope.REQUEST, 1L, 7L, "firewall.port", "443", "기능명세서 4쪽");

        assertThatThrownBy(() -> answer.confirmByStaff(" ", NOW)).isInstanceOf(DomainRuleViolationException.class);
        answer.confirmByStaff("security", NOW);
        assertThat(answer.getValueSource()).isEqualTo(ValueSource.STAFF_CONFIRMED);

        Answer empty = Answer.staffCheckNeeded(AnswerScope.CASE, 1L, null, "network.source");
        assertThatThrownBy(() -> empty.confirmByStaff("infra", NOW)).isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("재검토 표시는 값·출처·확인을 보존하고, 사용자가 다시 답하면 해제된다(불변식 11)")
    void needsReviewKeepsValue() {
        Answer answer = Answer.userAnswer(AnswerScope.CASE, 1L, null, "project.name", "민원 포털");

        answer.markNeedsReview();
        assertThat(answer.isNeedsReview()).isTrue();
        assertThat(answer.getValue()).isEqualTo("민원 포털");
        assertThat(answer.isConfirmed()).isTrue();

        answer.updateByUser("민원 포털 2.0");
        assertThat(answer.isNeedsReview()).isFalse();
    }

    @Test
    @DisplayName("서류별 답변에는 신청 ID 가 필요하고 공통 답변에는 없어야 한다")
    void scopeOwnerRule() {
        assertThatThrownBy(() -> Answer.userAnswer(AnswerScope.REQUEST, 1L, null, "firewall.port", "443"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> Answer.userAnswer(AnswerScope.CASE, 1L, 7L, "project.name", "값"))
                .isInstanceOf(DomainRuleViolationException.class);
    }
}
