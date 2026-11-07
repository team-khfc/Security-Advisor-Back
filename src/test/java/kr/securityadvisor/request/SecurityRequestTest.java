package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.domain.SubmissionPreconditions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 신청 애그리거트의 단계×상태 전이(불변식 1·2·3·11·12·13·33·34·35)를 프레임워크 없이 검증한다. */
class SecurityRequestTest {

    private static SecurityRequest inFinalReview() {
        SecurityRequest request = SecurityRequest.create(1L, DocTypeCode.of("FIREWALL"));
        request.requestReview();
        request.completeReviews(true);
        return request;
    }

    @Test
    @DisplayName("새 신청은 1단계 작성 중이다")
    void created() {
        SecurityRequest request = SecurityRequest.create(1L, DocTypeCode.of("FIREWALL"));

        assertThat(request.getStage()).isEqualTo(1);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.DRAFTING);
        assertThat(request.isUnsubmitted()).isTrue();
    }

    @Test
    @DisplayName("정상 흐름: 작성 중 → 협의 중 → 최종 검토 중 → 제출됨 → 확정")
    void happyPath() {
        SecurityRequest request = SecurityRequest.create(1L, DocTypeCode.of("FIREWALL"));

        request.requestReview();
        assertThat(request.getStage()).isEqualTo(2);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_REVIEW);

        request.completeReviews(true);
        assertThat(request.getStage()).isEqualTo(3);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.FINAL_REVIEW);

        request.submit(SubmissionPreconditions.satisfied());
        assertThat(request.getStage()).isEqualTo(4);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(request.isUnsubmitted()).isFalse();

        request.confirm(true);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CONFIRMED);
        assertThat(request.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("협의 완료 전이는 두 분야 협의가 모두 끝나야 한다 — 할당 완료만으로는 불가(불변식 12)")
    void completeReviewsRequiresAll() {
        SecurityRequest request = SecurityRequest.create(1L, DocTypeCode.of("FIREWALL"));
        request.requestReview();

        assertThatThrownBy(() -> request.completeReviews(false)).isInstanceOf(DomainRuleViolationException.class);
        assertThat(request.getStage()).isEqualTo(2);
    }

    @Test
    @DisplayName("미확정 값이 있으면 제출할 수 없다(불변식 1)")
    void submitRejectsUnconfirmed() {
        SecurityRequest request = inFinalReview();

        assertThatThrownBy(() -> request.submit(new SubmissionPreconditions(List.of(), List.of("network.source"), 0, true)))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("network.source");
        assertThat(request.getStage()).isEqualTo(3);
    }

    @Test
    @DisplayName("필수값 누락·미해결 보완·협의 미완료는 각각 제출을 막는다(불변식 2·3)")
    void submitRejectsOtherConditions() {
        assertThatThrownBy(() -> inFinalReview().submit(new SubmissionPreconditions(List.of("firewall.port"), List.of(), 0, true)))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> inFinalReview().submit(new SubmissionPreconditions(List.of(), List.of(), 1, true)))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> inFinalReview().submit(new SubmissionPreconditions(List.of(), List.of(), 0, false)))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("1단계에서는 제출할 수 없다")
    void submitOnlyFromFinalReview() {
        SecurityRequest request = SecurityRequest.create(1L, DocTypeCode.of("FIREWALL"));

        assertThatThrownBy(() -> request.submit(SubmissionPreconditions.satisfied()))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("보완 요청은 협의 중·최종 검토 중에서만, 답변하면 원래 상태로 돌아온다")
    void supplementCycle() {
        SecurityRequest request = SecurityRequest.create(1L, DocTypeCode.of("FIREWALL"));
        assertThatThrownBy(request::requestSupplement).isInstanceOf(DomainRuleViolationException.class);

        request.requestReview();
        request.requestSupplement();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.SUPPLEMENT_REQUESTED);
        assertThat(request.getStage()).isEqualTo(2);
        request.answerSupplement();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_REVIEW);

        request.completeReviews(true);
        request.requestSupplement();
        request.answerSupplement();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.FINAL_REVIEW);
    }

    @ParameterizedTest(name = "{0}단계 → {1}단계 반환")
    @CsvSource({"2,1,DRAFTING", "3,2,IN_REVIEW", "3,1,DRAFTING", "4,3,FINAL_REVIEW"})
    @DisplayName("허용된 차수 반환은 대상 단계의 첫 상태로 간다")
    void allowedReturns(int from, int to, RequestStatus expected) {
        SecurityRequest request = atStage(from);

        request.returnToStage(to, "항목 보완 필요");

        assertThat(request.getStage()).isEqualTo(to);
        assertThat(request.getStatus()).isEqualTo(expected);
    }

    @Test
    @DisplayName("허용되지 않는 반환(1→?, 2→3, 4→1)과 사유 없는 반환은 거부한다(불변식 11)")
    void disallowedReturns() {
        assertThatThrownBy(() -> atStage(1).returnToStage(1, "사유")).isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> atStage(2).returnToStage(3, "사유")).isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> atStage(4).returnToStage(1, "사유")).isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> atStage(2).returnToStage(1, " ")).isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("종결·철회는 사유가 필수이고, 이후 어떤 전이도 불가하다(불변식 13·35)")
    void closeAndWithdrawAreTerminal() {
        SecurityRequest closed = atStage(2);
        assertThatThrownBy(() -> closed.close(null)).isInstanceOf(DomainRuleViolationException.class);
        closed.close("신청 대상이 아님");
        assertThat(closed.getStatus()).isEqualTo(RequestStatus.CLOSED);
        assertThat(closed.getStage()).isEqualTo(2);
        assertThatThrownBy(() -> closed.returnToStage(1, "사유")).isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> closed.withdraw("사유")).isInstanceOf(DomainRuleViolationException.class);

        SecurityRequest withdrawn = SecurityRequest.create(1L, DocTypeCode.of("SECURITY_REVIEW"));
        withdrawn.withdraw("사업 취소");
        assertThat(withdrawn.getStatus()).isEqualTo(RequestStatus.WITHDRAWN);
        assertThatThrownBy(withdrawn::requestReview).isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("확정은 두 분야 최종 확인이 모두 끝나야 하고, 확정 뒤에는 반환할 수 없다(불변식 34·35)")
    void confirmRules() {
        SecurityRequest request = atStage(4);
        assertThatThrownBy(() -> request.confirm(false)).isInstanceOf(DomainRuleViolationException.class);

        request.confirm(true);

        assertThatThrownBy(() -> request.returnToStage(3, "사유")).isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("오래된 revision 으로 쓰면 ConflictException(409)(불변식 10)")
    void staleRevision() {
        SecurityRequest request = SecurityRequest.create(1L, DocTypeCode.of("FIREWALL"));

        request.requireRevision(0);
        assertThatThrownBy(() -> request.requireRevision(5)).isInstanceOf(ConflictException.class);
    }

    private static SecurityRequest atStage(int stage) {
        SecurityRequest request = SecurityRequest.create(1L, DocTypeCode.of("FIREWALL"));
        if (stage >= 2) {
            request.requestReview();
        }
        if (stage >= 3) {
            request.completeReviews(true);
        }
        if (stage >= 4) {
            request.submit(SubmissionPreconditions.satisfied());
        }
        return request;
    }
}
