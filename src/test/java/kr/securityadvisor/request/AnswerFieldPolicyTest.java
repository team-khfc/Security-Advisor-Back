package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.*;

import java.util.Set;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.ForbiddenException;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.request.domain.AnswerFieldPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 입력·확인·AI 적용이 공유하는 게시본 규칙을 프레임워크 없이 검증한다. */
class AnswerFieldPolicyTest {
    private FieldDefinition field(String type, MemberRole role) {
        return new FieldDefinition("synthetic", "합성 항목", AnswerScope.CASE, Set.of(), type, true, role, null);
    }

    @Test
    @DisplayName("작성은 소유 역할만, 신청자 답변 확인은 담당자도 허용한다")
    void roleMatrix() {
        for (MemberRole owner : new MemberRole[] {MemberRole.APPLICANT, MemberRole.INFRA, MemberRole.SECURITY}) {
            for (MemberRole actor : MemberRole.values()) {
                var field = field("TEXT", owner);
                if (owner == actor)
                    assertThatCode(() -> AnswerFieldPolicy.requireWrite(field, actor))
                            .doesNotThrowAnyException();
                else
                    assertThatThrownBy(() -> AnswerFieldPolicy.requireWrite(field, actor))
                            .isInstanceOf(ForbiddenException.class);
                if (actor == owner
                        || owner == MemberRole.APPLICANT && (actor == MemberRole.INFRA || actor == MemberRole.SECURITY))
                    assertThatCode(() -> AnswerFieldPolicy.requireConfirm(field, actor))
                            .doesNotThrowAnyException();
                else
                    assertThatThrownBy(() -> AnswerFieldPolicy.requireConfirm(field, actor))
                            .isInstanceOf(ForbiddenException.class);
            }
        }
    }

    @Test
    @DisplayName("예·아니요·숫자·날짜 경계와 필수 미해당을 검사한다")
    void formats() {
        for (String value : new String[] {"true", "false", "예", "아니요"}) valid("BOOLEAN", value);
        valid("NUMBER", "-1.25");
        valid("DATE_RANGE", "2028-02-29~2028-03-01");
        valid("DATE_RANGE", "2028-02-29~");
        invalid("BOOLEAN", "아마도");
        invalid("NUMBER", "NaN");
        invalid("NUMBER", "1e999");
        invalid("DATE_RANGE", "2027-02-29~2027-03-01");
        invalid("DATE_RANGE", "2028-03-01~2028-02-29");
        invalid("DATE_RANGE", "2028-01-01~2028-02-01~2028-03-01");
        invalid("TEXT", "미해당");
        invalid("TEXT", " ");
        invalid("TEXT", "x".repeat(4001));
    }

    private void valid(String type, String value) {
        assertThatCode(() -> AnswerFieldPolicy.validate(field(type, MemberRole.APPLICANT), value))
                .doesNotThrowAnyException();
    }

    private void invalid(String type, String value) {
        assertThatThrownBy(() -> AnswerFieldPolicy.validate(field(type, MemberRole.APPLICANT), value))
                .isInstanceOf(DomainRuleViolationException.class);
    }
}
