package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Set;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.*;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.request.domain.*;
import org.junit.jupiter.api.Test;

/** 네트워크 입력 경계와 미해당 상태 전환을 검증한다. */
class AnswerFormatsTest {
    private FieldDefinition field(String type, boolean required) {
        return new FieldDefinition(
                "custom.synthetic", "합성 항목", AnswerScope.CASE, Set.of(), type, required, MemberRole.APPLICANT, null);
    }

    @Test
    void networkBoundaries() {
        for (String value : new String[] {
            "192.0.2.1", "192.0.2.0/24", "2001:db8::1/128", "::/0", "::ffff:192.0.2.1", "192.0.2.1,2001:db8::1"
        }) valid("IP_ADDRESS", value);
        for (String value : new String[] {
            "256.1.1.1",
            "127.1",
            "01.2.3.4",
            "localhost",
            "192.0.2.1/33",
            "2001:db8::/129",
            "1:::2",
            "1:2:3:4:5:6:7:8::",
            "::%eth0",
            "192.0.2.1,"
        }) invalid("IP_ADDRESS", value);
        for (String value : new String[] {"1", "65535", "443, 8000-8080\n53"}) valid("PORT", value);
        for (String value : new String[] {"0", "65536", "8000-443", "1.2", "443,", "all"}) invalid("PORT", value);
        valid("PROTOCOL", "TCP, UDP\nICMPV6");
        invalid("PROTOCOL", "HTTP");
        invalid("PROTOCOL", "tcp");
    }

    @Test
    void notApplicableTransitionsKeepProvenanceAndConfirmation() {
        var optional = field("PORT", false);
        String value = AnswerFieldPolicy.normalize(optional, null, true);
        var answer = Answer.userAnswer(AnswerScope.CASE, 1L, null, optional.key(), value);
        assertThat(answer.isNotApplicable()).isTrue();
        answer.markNeedsReview();
        assertThat(answer.isNotApplicable()).isTrue();
        answer.confirmByStaff("synthetic", Instant.now());
        assertThat(answer.isNotApplicable()).isTrue();
        answer.updateByUser("443");
        assertThat(answer.isNotApplicable()).isFalse();
        var ai = Answer.aiInferred(AnswerScope.CASE, 1L, null, optional.key(), "미해당", "합성 근거");
        assertThat(ai.isNotApplicable()).isTrue();
        assertThat(ai.isUnconfirmed()).isTrue();
        assertThatThrownBy(() -> AnswerFieldPolicy.normalize(field("PORT", true), null, true))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> AnswerFieldPolicy.normalize(optional, "443", true))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    private void valid(String type, String value) {
        assertThatCode(() -> AnswerFieldPolicy.validate(field(type, true), value))
                .doesNotThrowAnyException();
    }

    private void invalid(String type, String value) {
        assertThatThrownBy(() -> AnswerFieldPolicy.validate(field(type, true), value))
                .isInstanceOf(DomainRuleViolationException.class);
    }
}
