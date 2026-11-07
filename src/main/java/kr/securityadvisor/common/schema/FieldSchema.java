package kr.securityadvisor.common.schema;

import java.util.List;
import java.util.Optional;

/**
 * 게시본에 고정된 필드 스키마 전체. 사업 건은 생성 시점 게시본의 스키마를 끝까지 쓴다(불변식 8).
 *
 * @param fields 항목 목록(키는 유일)
 */
public record FieldSchema(List<FieldDefinition> fields) {

    public Optional<FieldDefinition> find(String key) {
        return fields.stream().filter(field -> field.key().equals(key)).findFirst();
    }

    /** 사업 건 공통 항목. */
    public List<FieldDefinition> caseFields() {
        return fields.stream().filter(field -> field.scope() == AnswerScope.CASE).toList();
    }

    /** 특정 서류의 서류별 항목. */
    public List<FieldDefinition> requestFields(DocType docType) {
        return fields.stream()
                .filter(field -> field.scope() == AnswerScope.REQUEST && field.appliesTo(docType))
                .toList();
    }

    /** 제출 전 검사에 쓰는 필수 항목(공통 + 해당 서류). */
    public List<FieldDefinition> requiredFieldsFor(DocType docType) {
        return fields.stream()
                .filter(FieldDefinition::required)
                .filter(field -> field.scope() == AnswerScope.CASE || field.appliesTo(docType))
                .toList();
    }
}
