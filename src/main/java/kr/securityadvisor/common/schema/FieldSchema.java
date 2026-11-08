package kr.securityadvisor.common.schema;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 게시본에 고정된 서류 유형 목록 + 필드 스키마 전체. 사업 건은 생성 시점 게시본을 끝까지 쓴다(불변식 8).
 *
 * @param deadlinePolicy 처리 기한 정책(없으면 기본값)
 * @param docTypes 이 게시본이 제공하는 서류 유형(ADR 0005 — 유형은 데이터)
 * @param fields 항목 목록(키는 유일)
 */
public record FieldSchema(
        DeadlinePolicy deadlinePolicy, List<DocTypeDefinition> docTypes, List<FieldDefinition> fields) {

    public FieldSchema {
        deadlinePolicy = deadlinePolicy == null ? DeadlinePolicy.defaults() : deadlinePolicy;
        docTypes = docTypes == null ? List.of() : docTypes;
        fields = fields == null ? List.of() : fields;
    }

    public Optional<FieldDefinition> find(String key) {
        return fields.stream().filter(field -> field.key().equals(key)).findFirst();
    }

    public Optional<DocTypeDefinition> findDocType(DocTypeCode code) {
        return docTypes.stream().filter(docType -> docType.code().equals(code)).findFirst();
    }

    /** 새 사업 건에서 고를 수 있는 서류 유형(활성, 정렬 순). */
    public List<DocTypeDefinition> activeDocTypes() {
        return docTypes.stream()
                .filter(DocTypeDefinition::active)
                .sorted(Comparator.comparingInt(DocTypeDefinition::sortOrder))
                .toList();
    }

    /** 사업 건 공통 항목. */
    public List<FieldDefinition> caseFields() {
        return fields.stream()
                .filter(field -> field.scope() == AnswerScope.CASE)
                .toList();
    }

    /** 특정 서류의 서류별 항목. */
    public List<FieldDefinition> requestFields(DocTypeCode docType) {
        return fields.stream()
                .filter(field -> field.scope() == AnswerScope.REQUEST && field.appliesTo(docType))
                .toList();
    }

    /**
     * 제출 전 검사에 쓰는 필수 항목(공통 + 해당 서류). 공통 항목도 {@code docTypes} 를 적어 두었으면 그 서류에만 필수다 —
     * 예: INFRA 가 채우는 서버·출발지·목적지는 INFRA 협의가 없는 서류 유형에 요구하지 않는다(적용 서류 유형은 데이터 계약).
     */
    public List<FieldDefinition> requiredFieldsFor(DocTypeCode docType) {
        return fields.stream()
                .filter(FieldDefinition::required)
                .filter(field -> field.appliesTo(docType))
                .toList();
    }
}
