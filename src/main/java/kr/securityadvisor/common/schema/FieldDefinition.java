package kr.securityadvisor.common.schema;

import java.util.Set;
import kr.securityadvisor.common.security.MemberRole;

/**
 * 게시본 필드 스키마의 항목 하나(F-08 데이터 계약). 저장 키·범위·적용 서류 유형은 답변 저장의 계약이다.
 *
 * @param key 저장 키(예: {@code network.source})
 * @param label 화면 문구
 * @param scope CASE(사업 건 공통) / REQUEST(서류별)
 * @param docTypes 적용 서류 유형 코드(REQUEST 범위는 하나 이상, CASE 범위는 비우면 전체)
 * @param type 값 형식(TEXT·LONG_TEXT·BOOLEAN·DATE_RANGE·NUMBER)
 * @param required 제출 전 필수 여부
 * @param askRole 누가 답하는 항목인가(APPLICANT 또는 담당자 역할). 담당자 항목은 신청자가 답하지 않고 "담당자 확인 필요"로 남긴다
 * @param hint 작성 이유·근거 안내
 */
public record FieldDefinition(
        String key,
        String label,
        AnswerScope scope,
        Set<DocTypeCode> docTypes,
        String type,
        boolean required,
        MemberRole askRole,
        String hint) {

    public boolean appliesTo(DocTypeCode docType) {
        return docTypes == null || docTypes.isEmpty() || docTypes.contains(docType);
    }

    public boolean isStaffField() {
        return askRole != null && askRole != MemberRole.APPLICANT;
    }
}
