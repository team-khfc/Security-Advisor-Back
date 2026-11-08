package kr.securityadvisor.configuration.service;

import java.util.HashSet;
import java.util.Set;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.schema.DocTypeDefinition;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import org.springframework.stereotype.Component;

/** 설정 스키마의 데이터 계약 검증(F-08): 서류 유형 코드·저장 키 유일, REQUEST 항목은 적용 서류 1개 이상, 참조 무결성. */
@Component
public class FieldSchemaValidator {

    public void validate(FieldSchema schema) {
        if (schema.docTypes().isEmpty()) {
            throw new DomainRuleViolationException("서류 유형이 하나 이상 필요합니다");
        }
        Set<DocTypeCode> codes = new HashSet<>();
        boolean anyActive = false;
        for (DocTypeDefinition d : schema.docTypes()) {
            if (d.code() == null || d.name() == null || d.name().isBlank()) {
                throw new DomainRuleViolationException("서류 유형에는 코드와 이름이 필요합니다");
            }
            if (!codes.add(d.code())) {
                throw new DomainRuleViolationException("서류 유형 코드가 중복됩니다: " + d.code());
            }
            anyActive |= d.active();
        }
        if (!anyActive) {
            throw new DomainRuleViolationException("활성 서류 유형이 하나 이상 필요합니다");
        }
        Set<String> keys = new HashSet<>();
        for (FieldDefinition f : schema.fields()) {
            if (f.key() == null || !f.key().matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")) {
                throw new DomainRuleViolationException("저장 키 형식이 잘못되었습니다(예: network.source): " + f.key());
            }
            if (!keys.add(f.key())) {
                throw new DomainRuleViolationException("저장 키가 중복됩니다: " + f.key());
            }
            if (f.label() == null || f.label().isBlank() || f.scope() == null || f.askRole() == null) {
                throw new DomainRuleViolationException("항목 " + f.key() + " 에 문구·범위·작성 역할이 필요합니다");
            }
            if (f.scope() == AnswerScope.REQUEST
                    && (f.docTypes() == null || f.docTypes().isEmpty())) {
                throw new DomainRuleViolationException("서류별(REQUEST) 항목 " + f.key() + " 은 적용 서류 유형이 하나 이상 필요합니다");
            }
            if (f.docTypes() != null) {
                for (DocTypeCode code : f.docTypes()) {
                    if (!codes.contains(code)) {
                        throw new DomainRuleViolationException("항목 " + f.key() + " 이 없는 서류 유형을 참조합니다: " + code);
                    }
                }
            }
        }
    }
}
