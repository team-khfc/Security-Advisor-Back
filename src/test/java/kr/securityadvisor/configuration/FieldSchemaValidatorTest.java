package kr.securityadvisor.configuration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.schema.DocTypeDefinition;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.configuration.service.FieldSchemaValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 설정 스키마 데이터 계약: 코드·키 유일, REQUEST 항목의 적용 서류, 참조 무결성. */
class FieldSchemaValidatorTest {

    private final FieldSchemaValidator validator = new FieldSchemaValidator();
    private final DocTypeDefinition firewall = new DocTypeDefinition(
            DocTypeCode.of("FIREWALL"),
            "방화벽",
            null,
            true,
            "firewall-v1",
            true,
            Set.of(MemberRole.INFRA, MemberRole.SECURITY),
            1);

    @Test
    @DisplayName("정상 스키마는 통과하고, 키 중복·형식 오류·없는 서류 참조·REQUEST 항목 서류 누락은 거부한다")
    void rules() {
        FieldDefinition ok = field("firewall.port", AnswerScope.REQUEST, Set.of(DocTypeCode.of("FIREWALL")));
        assertThatCode(() -> validator.validate(new FieldSchema(null, List.of(firewall), List.of(ok))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(new FieldSchema(null, List.of(firewall), List.of(ok, ok))))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("중복");
        assertThatThrownBy(() -> validator.validate(new FieldSchema(
                        null, List.of(firewall), List.of(field("Bad Key", AnswerScope.CASE, Set.of())))))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("형식");
        assertThatThrownBy(() -> validator.validate(
                        new FieldSchema(null, List.of(firewall), List.of(field("x.y", AnswerScope.REQUEST, Set.of())))))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("적용 서류");
        assertThatThrownBy(() -> validator.validate(new FieldSchema(
                        null,
                        List.of(firewall),
                        List.of(field("x.y", AnswerScope.CASE, Set.of(DocTypeCode.of("NOPE")))))))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("없는 서류");
        assertThatThrownBy(() -> validator.validate(new FieldSchema(null, List.of(), List.of())))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    private static FieldDefinition field(String key, AnswerScope scope, Set<DocTypeCode> docTypes) {
        return new FieldDefinition(key, "문구", scope, docTypes, "TEXT", true, MemberRole.APPLICANT, null);
    }
}
