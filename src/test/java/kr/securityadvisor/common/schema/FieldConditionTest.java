package kr.securityadvisor.common.schema;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.configuration.service.FieldSchemaValidator;
import kr.securityadvisor.document.service.DocumentRenderer;
import kr.securityadvisor.request.domain.SecurityRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FieldConditionTest {
    private final FieldDefinition parent = new FieldDefinition(
            "data.personal_info",
            "개인정보 처리 여부",
            AnswerScope.CASE,
            Set.of(),
            "BOOLEAN",
            true,
            MemberRole.APPLICANT,
            null);
    private final FieldDefinition child = new FieldDefinition(
            "security.controls",
            "개인정보 보호조치",
            AnswerScope.REQUEST,
            Set.of(DocTypeCode.of("SECURITY_REVIEW")),
            "LONG_TEXT",
            true,
            MemberRole.SECURITY,
            null,
            new FieldCondition("data.personal_info", true));

    private FieldSchema schema(FieldDefinition... fields) {
        return new FieldSchema(
                null,
                List.of(new DocTypeDefinition(
                        DocTypeCode.of("SECURITY_REVIEW"),
                        "보안성검토",
                        null,
                        true,
                        "security",
                        false,
                        Set.of(MemberRole.SECURITY, MemberRole.INFRA),
                        1)),
                List.of(fields));
    }

    @Test
    @DisplayName("예·아니요·미입력 조건을 구분하고 이전 게시본 질문은 계속 적용한다")
    void evaluatesWithoutGuessing() {
        assertThat(child.isApplicable(key -> "true")).isTrue();
        assertThat(child.isApplicable(key -> "예")).isTrue();
        assertThat(child.isApplicable(key -> "false")).isFalse();
        assertThat(child.isApplicable(key -> null)).isFalse();
        assertThat(child.isApplicable(key -> "미해당")).isFalse();
        assertThat(parent.isApplicable(key -> null)).isTrue();
    }

    @Test
    @DisplayName("게시 전 조건 참조 누락·자기 참조·기본 질문 계약 변경을 거부한다")
    void validatesPublication() {
        FieldSchemaValidator validator = new FieldSchemaValidator();
        validator.validate(schema(parent, child));
        assertThatThrownBy(() -> validator.validate(schema(child))).isInstanceOf(DomainRuleViolationException.class);
        var self = new FieldDefinition(
                parent.key(),
                parent.label(),
                parent.scope(),
                parent.docTypes(),
                parent.type(),
                true,
                parent.askRole(),
                null,
                new FieldCondition(parent.key(), true));
        assertThatThrownBy(() -> validator.validate(schema(self))).isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> validator.validateBuiltIns(schema(self), schema(parent)))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    @DisplayName("현재 값 대신 제출본 조건을 사용해 최종 문서에서 미적용 항목을 제외한다")
    void rendersSnapshotConditions() {
        SecurityRequest request = mock(SecurityRequest.class);
        when(request.getDocType()).thenReturn(DocTypeCode.of("SECURITY_REVIEW"));
        DocumentRenderer renderer = new DocumentRenderer();
        var no = Map.of(
                parent.key(),
                new DocumentRenderer.Source("false", ValueSource.USER_ANSWER, true, false),
                child.key(),
                new DocumentRenderer.Source("이전 조건에서 작성한 내용", ValueSource.STAFF_INPUT, true, false));
        assertThat(renderer.sections(schema(parent, child), request, no).get(1).values())
                .isEmpty();
        var yes = Map.of(parent.key(), new DocumentRenderer.Source("true", ValueSource.USER_ANSWER, true, false));
        assertThat(renderer.sections(schema(parent, child), request, yes).get(1).values())
                .hasSize(1);
    }
}
