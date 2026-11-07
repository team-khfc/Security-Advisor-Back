package kr.securityadvisor.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.schema.DocTypeDefinition;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 서류 유형이 데이터인 게시본 스키마(ADR 0005)의 조회 규칙. */
class FieldSchemaTest {

    private static final DocTypeCode FIREWALL = DocTypeCode.of("FIREWALL");
    private static final DocTypeCode USB = DocTypeCode.of("USB_REQUEST");

    private static final FieldSchema SCHEMA = new FieldSchema(
            null,
            List.of(
                    new DocTypeDefinition(USB, "USB 사용 신청서", null, true, "usb-v1", false, Set.of(MemberRole.SECURITY), 2),
                    new DocTypeDefinition(FIREWALL, "방화벽", null, true, "fw-v1", true, Set.of(MemberRole.INFRA, MemberRole.SECURITY), 1),
                    new DocTypeDefinition(DocTypeCode.of("OLD_TYPE"), "폐지", null, false, "old", false, Set.of(), 3)),
            List.of(
                    new FieldDefinition("project.name", "사업명", AnswerScope.CASE, Set.of(), "TEXT", true, MemberRole.APPLICANT, null),
                    new FieldDefinition("usb.purpose", "목적", AnswerScope.REQUEST, Set.of(USB), "TEXT", true, MemberRole.APPLICANT, null),
                    new FieldDefinition("firewall.port", "포트", AnswerScope.REQUEST, Set.of(FIREWALL), "TEXT", false, MemberRole.APPLICANT, null)));

    @Test
    @DisplayName("코드 작업 없이 데이터로 추가한 서류 유형(USB)이 활성 목록·서류별 항목·필수 항목에 반영된다")
    void dataDrivenDocType() {
        assertThat(SCHEMA.activeDocTypes()).extracting(DocTypeDefinition::code).containsExactly(FIREWALL, USB);
        assertThat(SCHEMA.findDocType(DocTypeCode.of("OLD_TYPE"))).get().extracting(DocTypeDefinition::active).isEqualTo(false);
        assertThat(SCHEMA.requestFields(USB)).extracting(FieldDefinition::key).containsExactly("usb.purpose");
        assertThat(SCHEMA.requiredFieldsFor(USB)).extracting(FieldDefinition::key).containsExactly("project.name", "usb.purpose");
        assertThat(SCHEMA.requiredFieldsFor(FIREWALL)).extracting(FieldDefinition::key).containsExactly("project.name");
    }

    @Test
    @DisplayName("유형 코드는 대문자·숫자·밑줄 2~30자이며 소문자 입력은 대문자로 정규화한다")
    void docTypeCodeFormat() {
        assertThat(DocTypeCode.of(" firewall ")).isEqualTo(FIREWALL);
        assertThatThrownBy(() -> DocTypeCode.of("usb request")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocTypeCode.of("1ABC")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocTypeCode.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
