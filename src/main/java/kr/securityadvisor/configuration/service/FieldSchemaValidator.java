package kr.securityadvisor.configuration.service;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.DeadlinePolicy;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.schema.DocTypeDefinition;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.security.MemberRole;
import org.springframework.stereotype.Component;

/** 설정 스키마의 데이터 계약 검증(F-08): 서류 유형 코드·저장 키 유일, REQUEST 항목은 적용 서류 1개 이상, 참조 무결성. */
@Component
public class FieldSchemaValidator {

    public void validate(FieldSchema schema) {
        validateDeadline(schema.deadlinePolicy());
        if (schema.fields().size() > 200) {
            throw new DomainRuleViolationException("질문은 최대 200개까지 설정할 수 있습니다");
        }
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
            if (d.reviewFields() == null
                    || d.reviewFields().isEmpty()
                    || !Set.of(MemberRole.INFRA, MemberRole.SECURITY).containsAll(d.reviewFields())) {
                throw new DomainRuleViolationException("검토 분야는 ICT인프라·정보보안 중 하나 이상이어야 합니다");
            }
            anyActive |= d.active();
        }
        if (!anyActive) {
            throw new DomainRuleViolationException("활성 서류 유형이 하나 이상 필요합니다");
        }
        Set<String> keys = new HashSet<>();
        for (FieldDefinition f : schema.fields()) {
            if (f.key() == null || f.key().length() > 100 || !f.key().matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")) {
                throw new DomainRuleViolationException("저장 키 형식이 잘못되었습니다(예: network.source): " + f.key());
            }
            if (!keys.add(f.key())) {
                throw new DomainRuleViolationException("저장 키가 중복됩니다: " + f.key());
            }
            if (f.label() == null || f.label().isBlank() || f.scope() == null || f.askRole() == null) {
                throw new DomainRuleViolationException("항목 " + f.key() + " 에 문구·범위·작성 역할이 필요합니다");
            }
            if (f.label().length() > 200 || (f.hint() != null && f.hint().length() > 2000)) {
                throw new DomainRuleViolationException("질문 문구는 200자, 도움말은 2000자 이하여야 합니다: " + f.key());
            }
            if (f.type() == null
                    || !Set.of("TEXT", "LONG_TEXT", "BOOLEAN", "DATE_RANGE", "NUMBER")
                            .contains(f.type())) {
                throw new DomainRuleViolationException("지원하지 않는 응답 형식입니다: " + f.key());
            }
            if (f.askRole() == MemberRole.ADMIN) {
                throw new DomainRuleViolationException("질문 작성 역할은 신청자·ICT인프라·정보보안 중 하나여야 합니다");
            }
            if (f.isStaffField()
                    && schema.docTypes().stream()
                            .filter(fType -> f.appliesTo(fType.code()))
                            .anyMatch(d -> !d.reviewFields().contains(f.askRole()))) {
                throw new DomainRuleViolationException("담당자 질문의 작성 역할이 해당 서류의 검토 분야에 없습니다: " + f.key());
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

    /** 기본 질문은 사업 생성·통신 규칙과 연결된 계약을 유지한다. 문구와 도움말은 편집할 수 있다. */
    public void validateBuiltIns(FieldSchema draft, FieldSchema initial) {
        for (FieldDefinition original : initial.fields()) {
            FieldDefinition field = draft.fields().stream()
                    .filter(f -> f.key().equals(original.key()))
                    .findFirst()
                    .orElseThrow(
                            () -> new DomainRuleViolationException("기본 질문은 삭제하거나 저장 키를 바꿀 수 없습니다: " + original.key()));
            if (field.scope() != original.scope()
                    || !Objects.equals(field.docTypes(), original.docTypes())
                    || !Objects.equals(field.type(), original.type())
                    || field.askRole() != original.askRole()
                    || (original.required() && !field.required())) {
                throw new DomainRuleViolationException("기본 질문의 범위·적용 서류·형식·역할·필수 계약을 유지해야 합니다: " + original.key());
            }
        }
    }

    private void validateDeadline(DeadlinePolicy policy) {
        for (int days : new int[] {
            policy.reviewBusinessDays(), policy.supplementBusinessDays(), policy.finalConfirmationBusinessDays()
        }) {
            if (days < 1 || days > 365) {
                throw new DomainRuleViolationException("처리 기한은 1~365 사이의 정수 영업일이어야 합니다");
            }
        }
        if (policy.imminentBusinessDays() != 1) {
            throw new DomainRuleViolationException("기한 임박 알림은 1영업일 전으로 고정됩니다");
        }
        if (policy.holidays().size() > 366
                || policy.holidays().stream().anyMatch(day -> day.getYear() < 2000 || day.getYear() > 2100)) {
            throw new DomainRuleViolationException("공휴일은 2000~2100년 날짜로 최대 366개까지 설정할 수 있습니다");
        }
    }
}
