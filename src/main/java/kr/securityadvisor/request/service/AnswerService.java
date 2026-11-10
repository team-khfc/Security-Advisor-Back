package kr.securityadvisor.request.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.request.domain.Answer;
import kr.securityadvisor.request.domain.AnswerFieldPolicy;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.AnswerActionRequest;
import kr.securityadvisor.request.dto.AnswerBatchCommand;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.dto.AnswerUpdateRequest;
import kr.securityadvisor.request.repository.AnswerRepository;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 답변 유스케이스(F-03): 사용자 답변 저장, AI 제안 값 확인, 담당자 확인 요청. 공통(CASE)·서류별(REQUEST) 범위를 같은 규칙으로 다룬다.
 *
 * <p>항목은 사업 건에 고정된 게시본 스키마에 있어야 하고 범위·서류 유형이 맞아야 한다. 제출된 신청의 답변은 바꿀 수 없다
 * (제출본은 스냅샷, 불변식 17). 담당자 입력·확인(STAFF_INPUT·STAFF_CONFIRMED)은 review 컨텍스트가 배정 검사 후 호출한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final BusinessCaseRepository businessCaseRepository;
    private final SecurityRequestRepository securityRequestRepository;
    private final PublicationService publicationService;
    private final RequestAccessPolicy accessPolicy;
    private final AuditRecorder auditRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** 답변 변경 기록(F-14). 값 자체는 남기지 않고 항목·출처만 남긴다(기관 사실 값 보호, 불변식 25). */
    private void audit(MemberPrincipal actor, Answer answer, AuditAction action, String what) {
        boolean caseScope = answer.getScope() == AnswerScope.CASE;
        auditRecorder.record(
                actor,
                caseScope ? AuditTargetType.BUSINESS_CASE : AuditTargetType.SECURITY_REQUEST,
                String.valueOf(caseScope ? answer.getBusinessCaseId() : answer.getSecurityRequestId()),
                answer.getRevision(),
                action,
                null,
                answer.getFieldKey() + " " + what + " (출처 " + answer.getValueSource() + ")");
    }

    /** 공통 항목에 사용자 답변을 저장한다. 그 항목을 쓰는 미제출 신청에 같은 값이 보인다(scope=CASE). */
    public AnswerResponse answerCaseField(
            MemberPrincipal principal, Long businessCaseId, String fieldKey, AnswerUpdateRequest request) {
        BusinessCase businessCase = accessPolicy.ownedCase(principal, businessCaseId);
        requireCaseEditable(businessCase);
        FieldDefinition field = requireField(businessCase, fieldKey, AnswerScope.CASE, null);
        AnswerFieldPolicy.requireWrite(field, principal.role());
        AnswerFieldPolicy.validate(field, request.value());
        Answer answer = answerRepository
                .findByBusinessCaseIdAndScopeAndFieldKey(businessCaseId, AnswerScope.CASE, fieldKey)
                .map(existing -> {
                    requireRevision(existing, request.expectedRevision());
                    existing.updateByUser(request.value());
                    return existing;
                })
                .orElseGet(() -> answerRepository.save(
                        Answer.userAnswer(AnswerScope.CASE, businessCaseId, null, field.key(), request.value())));
        audit(principal, answer, AuditAction.ANSWER_UPDATED, "사용자 답변");
        eventPublisher.publishEvent(new AnswerChangedEvent(
                principal, unsubmittedRequestIds(businessCaseId), fieldKey, submittedRequestIds(businessCaseId)));
        return AnswerResponse.from(answer);
    }

    /** 서류별 항목에 사용자 답변을 저장한다. */
    public AnswerResponse answerRequestField(
            MemberPrincipal principal, Long securityRequestId, String fieldKey, AnswerUpdateRequest request) {
        SecurityRequest securityRequest = accessPolicy.ownedRequest(principal, securityRequestId);
        requireRequestEditable(securityRequest);
        BusinessCase businessCase = accessPolicy.viewableCase(principal, securityRequest.getBusinessCaseId());
        FieldDefinition field = requireField(businessCase, fieldKey, AnswerScope.REQUEST, securityRequest);
        AnswerFieldPolicy.requireWrite(field, principal.role());
        AnswerFieldPolicy.validate(field, request.value());
        Answer answer = answerRepository
                .findBySecurityRequestIdAndFieldKey(securityRequestId, fieldKey)
                .map(existing -> {
                    requireRevision(existing, request.expectedRevision());
                    existing.updateByUser(request.value());
                    return existing;
                })
                .orElseGet(() -> answerRepository.save(Answer.userAnswer(
                        AnswerScope.REQUEST,
                        securityRequest.getBusinessCaseId(),
                        securityRequestId,
                        field.key(),
                        request.value())));
        audit(principal, answer, AuditAction.ANSWER_UPDATED, "사용자 답변");
        eventPublisher.publishEvent(new AnswerChangedEvent(principal, List.of(securityRequestId), fieldKey));
        return AnswerResponse.from(answer);
    }

    /** 사용자가 공통 항목의 AI 제안 값을 확인한다(확인 전까지 미확정, 불변식 1). */
    public AnswerResponse confirmCaseAnswer(
            MemberPrincipal principal, Long businessCaseId, String fieldKey, AnswerActionRequest request) {
        BusinessCase businessCase = accessPolicy.ownedCase(principal, businessCaseId);
        requireCaseEditable(businessCase);
        FieldDefinition field = requireField(businessCase, fieldKey, AnswerScope.CASE, null);
        AnswerFieldPolicy.requireWrite(field, principal.role());
        Answer answer = existingCaseAnswer(businessCaseId, fieldKey);
        AnswerFieldPolicy.validate(field, answer.getValue());
        requireRevision(answer, request.expectedRevision());
        answer.confirmByUser(principal.username(), clock.instant());
        audit(principal, answer, AuditAction.ANSWER_CONFIRMED, "AI 제안 값 사용자 확인");
        return AnswerResponse.from(answer);
    }

    /** 사용자가 서류별 항목의 AI 제안 값을 확인한다. */
    public AnswerResponse confirmRequestAnswer(
            MemberPrincipal principal, Long securityRequestId, String fieldKey, AnswerActionRequest request) {
        SecurityRequest securityRequest = accessPolicy.ownedRequest(principal, securityRequestId);
        requireRequestEditable(securityRequest);
        FieldDefinition field =
                requireField(businessCaseOf(securityRequest), fieldKey, AnswerScope.REQUEST, securityRequest);
        AnswerFieldPolicy.requireWrite(field, principal.role());
        Answer answer = existingRequestAnswer(securityRequestId, fieldKey);
        AnswerFieldPolicy.validate(field, answer.getValue());
        requireRevision(answer, request.expectedRevision());
        answer.confirmByUser(principal.username(), clock.instant());
        audit(principal, answer, AuditAction.ANSWER_CONFIRMED, "AI 제안 값 사용자 확인");
        return AnswerResponse.from(answer);
    }

    /** 사용자가 모르는 공통 항목을 담당자 확인 요청으로 넘긴다(기관 내부 사실 등). 없는 항목이면 자리를 만든다. */
    public AnswerResponse requestStaffCheckOnCase(
            MemberPrincipal principal, Long businessCaseId, String fieldKey, AnswerActionRequest request) {
        BusinessCase businessCase = accessPolicy.ownedCase(principal, businessCaseId);
        requireCaseEditable(businessCase);
        FieldDefinition field = requireField(businessCase, fieldKey, AnswerScope.CASE, null);
        Answer answer = answerRepository
                .findByBusinessCaseIdAndScopeAndFieldKey(businessCaseId, AnswerScope.CASE, fieldKey)
                .map(existing -> {
                    requireRevision(existing, request.expectedRevision());
                    existing.markStaffCheckNeeded();
                    return existing;
                })
                .orElseGet(() -> answerRepository.save(
                        Answer.staffCheckNeeded(AnswerScope.CASE, businessCaseId, null, field.key())));
        audit(principal, answer, AuditAction.STAFF_CHECK_REQUESTED, "담당자 확인 요청");
        return AnswerResponse.from(answer);
    }

    /** 사용자가 모르는 서류별 항목을 담당자 확인 요청으로 넘긴다. */
    public AnswerResponse requestStaffCheckOnRequest(
            MemberPrincipal principal, Long securityRequestId, String fieldKey, AnswerActionRequest request) {
        SecurityRequest securityRequest = accessPolicy.ownedRequest(principal, securityRequestId);
        requireRequestEditable(securityRequest);
        BusinessCase businessCase = accessPolicy.viewableCase(principal, securityRequest.getBusinessCaseId());
        FieldDefinition field = requireField(businessCase, fieldKey, AnswerScope.REQUEST, securityRequest);
        Answer answer = answerRepository
                .findBySecurityRequestIdAndFieldKey(securityRequestId, fieldKey)
                .map(existing -> {
                    requireRevision(existing, request.expectedRevision());
                    existing.markStaffCheckNeeded();
                    return existing;
                })
                .orElseGet(() -> answerRepository.save(Answer.staffCheckNeeded(
                        AnswerScope.REQUEST, securityRequest.getBusinessCaseId(), securityRequestId, field.key())));
        audit(principal, answer, AuditAction.STAFF_CHECK_REQUESTED, "담당자 확인 요청");
        return AnswerResponse.from(answer);
    }

    /**
     * 담당자 입력(STAFF_INPUT — 예: INFRA 의 서버·IP·포트 할당). 배정 검사는 호출한 review 컨텍스트가 끝낸 뒤 부른다.
     * 범위가 CASE 인 항목은 사업 건에 저장되어 같은 사업 건의 다른 신청에도 보인다(3-2장).
     */
    public AnswerResponse inputByStaff(
            MemberPrincipal staff,
            SecurityRequest securityRequest,
            String fieldKey,
            String value,
            Long expectedRevision) {
        BusinessCase businessCase = accessPolicy.viewableCase(staff, securityRequest.getBusinessCaseId());
        requireRequestEditable(securityRequest);
        FieldDefinition field = requireAnyScopeField(businessCase, fieldKey, securityRequest);
        AnswerFieldPolicy.requireWrite(field, staff.role());
        AnswerFieldPolicy.validate(field, value);
        Answer answer = findForField(businessCase, securityRequest, field)
                .map(existing -> {
                    // "담당자 확인 필요" 자리(값 없음)를 처음 채울 때는 revision 없이도 된다. 값이 있던 항목은 충돌 검사.
                    if (existing.hasValue() || expectedRevision != null) {
                        requireRevision(existing, expectedRevision);
                    }
                    existing.inputByStaff(staff.username(), value, clock.instant());
                    return existing;
                })
                .orElseGet(() -> {
                    Answer created = newPlaceholder(businessCase, securityRequest, field);
                    created.inputByStaff(staff.username(), value, clock.instant());
                    return answerRepository.save(created);
                });
        audit(staff, answer, AuditAction.ANSWER_STAFF_INPUT, "담당자 입력");
        return AnswerResponse.from(answer);
    }

    /** 담당자가 기존 값(사용자 답변·AI 추론)을 확인한다 → STAFF_CONFIRMED(불변식 5). */
    public AnswerResponse confirmByStaff(
            MemberPrincipal staff, SecurityRequest securityRequest, String fieldKey, Long expectedRevision) {
        BusinessCase businessCase = accessPolicy.viewableCase(staff, securityRequest.getBusinessCaseId());
        requireRequestEditable(securityRequest);
        FieldDefinition field = requireAnyScopeField(businessCase, fieldKey, securityRequest);
        AnswerFieldPolicy.requireConfirm(field, staff.role());
        Answer answer = findForField(businessCase, securityRequest, field)
                .orElseThrow(() -> new NotFoundException("답변이 없는 항목입니다: " + fieldKey));
        requireRevision(answer, expectedRevision);
        AnswerFieldPolicy.validate(field, answer.getValue());
        answer.confirmByStaff(staff.username(), clock.instant());
        audit(staff, answer, AuditAction.ANSWER_STAFF_CONFIRMED, "담당자 확인");
        return AnswerResponse.from(answer);
    }

    /**
     * AI 제안(F-03): 출처 AI_INFERRED·미확정으로 저장한다. 확인된 값은 덮어쓰지 않는다(원칙 1 — 호출자가 사전에 거른다).
     * 신청자 항목(askRole=APPLICANT)에만 쓴다 — 담당자 항목은 llm 컨텍스트가 제안 대상에서 제외한다(원칙 3).
     *
     * @param evidenceReference 근거 참조(출처 표기 + 조각 ID). 프롬프트·응답 원문이 아니다
     */
    public AnswerResponse proposeByAi(
            MemberPrincipal principal,
            SecurityRequest securityRequest,
            String fieldKey,
            String value,
            String evidenceReference,
            String modelReference) {
        BusinessCase businessCase = accessPolicy.ownedCase(principal, securityRequest.getBusinessCaseId());
        requireRequestEditable(securityRequest);
        FieldDefinition field = requireAnyScopeField(businessCase, fieldKey, securityRequest);
        AnswerFieldPolicy.requireWrite(field, principal.role());
        AnswerFieldPolicy.validate(field, value);
        if (field.isStaffField()) {
            throw new DomainRuleViolationException("담당자 항목에는 AI 제안을 넣을 수 없습니다: " + fieldKey);
        }
        Answer answer = findForField(businessCase, securityRequest, field)
                .map(existing -> {
                    existing.proposeByAi(value, evidenceReference);
                    return existing;
                })
                .orElseGet(() -> answerRepository.save(
                        field.scope() == AnswerScope.CASE
                                ? Answer.aiInferred(
                                        AnswerScope.CASE,
                                        businessCase.getId(),
                                        null,
                                        field.key(),
                                        value,
                                        evidenceReference)
                                : Answer.aiInferred(
                                        AnswerScope.REQUEST,
                                        businessCase.getId(),
                                        securityRequest.getId(),
                                        field.key(),
                                        value,
                                        evidenceReference)));
        audit(
                principal,
                answer,
                AuditAction.ANSWER_AI_PROPOSED,
                "AI 제안 " + modelReference + " 근거 " + evidenceReference);
        return AnswerResponse.from(answer);
    }

    /** 공통 답변 일괄 저장(PATCH). 항목마다 answerCaseField 와 같은 규칙, 한 트랜잭션. */
    public List<AnswerResponse> answerCaseFields(
            MemberPrincipal principal, Long businessCaseId, AnswerBatchCommand command) {
        List<AnswerResponse> saved = new ArrayList<>();
        for (AnswerBatchCommand.Item item : command.answers()) {
            saved.add(answerCaseField(
                    principal,
                    businessCaseId,
                    item.fieldKey(),
                    new AnswerUpdateRequest(item.value(), item.expectedRevision())));
        }
        return saved;
    }

    /** 서류별 답변 일괄 저장(PATCH). 항목마다 answerRequestField 와 같은 규칙, 한 트랜잭션. */
    public List<AnswerResponse> answerRequestFields(
            MemberPrincipal principal, Long securityRequestId, AnswerBatchCommand command) {
        List<AnswerResponse> saved = new ArrayList<>();
        for (AnswerBatchCommand.Item item : command.answers()) {
            saved.add(answerRequestField(
                    principal,
                    securityRequestId,
                    item.fieldKey(),
                    new AnswerUpdateRequest(item.value(), item.expectedRevision())));
        }
        return saved;
    }

    /**
     * 다른 신청의 서류별(REQUEST) 답변을 새 신청에 사용자 답변으로 미리 채운다(연장 신청 등). overrides 의 키는 그 값으로 덮어쓴다.
     * 값이 없는 답변은 건너뛰고, 새 신청의 게시본에 없는 항목도 건너뛴다. 출처는 USER_ANSWER(신청자가 낸 신청이므로) 이며 미확인 상태다.
     *
     * @return 채운 항목 키
     */
    public List<String> prefillRequestAnswers(
            MemberPrincipal principal, SecurityRequest from, SecurityRequest to, Map<String, String> overrides) {
        BusinessCase businessCase = businessCaseOf(to);
        FieldSchema schema = publicationService.getFieldSchema(businessCase.getPublicationId());
        Map<String, String> values = new LinkedHashMap<>();
        for (Answer answer : answerRepository.findBySecurityRequestIdOrderByFieldKeyAsc(from.getId())) {
            if (answer.hasValue()) {
                values.put(answer.getFieldKey(), answer.getValue());
            }
        }
        values.putAll(overrides);
        List<String> filled = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            FieldDefinition field = schema.find(entry.getKey()).orElse(null);
            if (field == null
                    || field.isStaffField()
                    || field.scope() != AnswerScope.REQUEST
                    || !field.appliesTo(to.getDocType())
                    || entry.getValue() == null
                    || entry.getValue().isBlank()) {
                continue;
            }
            AnswerFieldPolicy.validate(field, entry.getValue());
            Answer answer = answerRepository
                    .findBySecurityRequestIdAndFieldKey(to.getId(), entry.getKey())
                    .map(existing -> {
                        existing.updateByUser(entry.getValue());
                        return existing;
                    })
                    .orElseGet(() -> answerRepository.save(Answer.userAnswer(
                            AnswerScope.REQUEST, businessCase.getId(), to.getId(), entry.getKey(), entry.getValue())));
            audit(principal, answer, AuditAction.ANSWER_UPDATED, "연장 신청 미리 채움(신청 " + from.getId() + " 기준)");
            filled.add(entry.getKey());
        }
        return filled;
    }

    /** 사업 건 공통 답변 + 이 신청의 서류별 답변(AI 제안의 컨텍스트). */
    @Transactional(readOnly = true)
    public List<AnswerResponse> allAnswers(SecurityRequest securityRequest) {
        BusinessCase businessCase = businessCaseOf(securityRequest);
        List<AnswerResponse> all = new ArrayList<>(
                answerRepository
                        .findByBusinessCaseIdAndScopeOrderByFieldKeyAsc(businessCase.getId(), AnswerScope.CASE)
                        .stream()
                        .map(AnswerResponse::from)
                        .toList());
        all.addAll(requestAnswers(securityRequest.getId()));
        return all;
    }

    /** 보완 요청·차수 반환으로 지정된 항목을 재검토 표시한다(값·출처·확인은 보존, 불변식 11). 없는 항목은 자리를 만든다. */
    public void markNeedsReview(SecurityRequest securityRequest, Collection<String> fieldKeys) {
        if (fieldKeys == null || fieldKeys.isEmpty()) {
            return;
        }
        BusinessCase businessCase = businessCaseOf(securityRequest);
        FieldSchema schema = publicationService.getFieldSchema(businessCase.getPublicationId());
        for (String fieldKey : fieldKeys) {
            FieldDefinition field =
                    schema.find(fieldKey).orElseThrow(() -> new NotFoundException("게시본에 없는 항목입니다: " + fieldKey));
            Answer answer = findForField(businessCase, securityRequest, field)
                    .orElseGet(() -> answerRepository.save(newPlaceholder(businessCase, securityRequest, field)));
            answer.markNeedsReview();
        }
    }

    /** 사업 건에서 제출됨(4단계, 확정 전) 상태인 신청 ID — 공통 답변이 바뀌면 그 담당자에게 알린다(제출본은 불변). */
    public List<Long> submittedRequestIds(Long businessCaseId) {
        return securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCaseId).stream()
                .filter(request -> request.getStatus() == RequestStatus.SUBMITTED)
                .map(SecurityRequest::getId)
                .toList();
    }

    /** 사업 건의 미제출 신청 ID(공통 답변 변경의 영향 범위). */
    public List<Long> unsubmittedRequestIds(Long businessCaseId) {
        return securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCaseId).stream()
                .filter(SecurityRequest::isUnsubmitted)
                .map(SecurityRequest::getId)
                .toList();
    }

    /** 신청의 서류별 답변 목록. */
    @Transactional(readOnly = true)
    public List<AnswerResponse> requestAnswers(Long securityRequestId) {
        return answerRepository.findBySecurityRequestIdOrderByFieldKeyAsc(securityRequestId).stream()
                .map(AnswerResponse::from)
                .toList();
    }

    private void requireCaseEditable(BusinessCase businessCase) {
        boolean anyUnsubmitted =
                securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCase.getId()).stream()
                        .anyMatch(SecurityRequest::isUnsubmitted);
        if (!anyUnsubmitted) {
            throw new DomainRuleViolationException("진행 중(미제출)인 신청이 없어 공통 답변을 바꿀 수 없습니다");
        }
    }

    private static void requireRequestEditable(SecurityRequest securityRequest) {
        if (!securityRequest.isUnsubmitted()) {
            throw new DomainRuleViolationException("제출·종결·철회된 신청의 답변은 바꿀 수 없습니다 (현재 "
                    + securityRequest.getStatus().label() + ")");
        }
    }

    /** 항목이 게시본 스키마에 있고 범위·서류 유형이 맞는지 검사한다. */
    private FieldDefinition requireField(
            BusinessCase businessCase, String fieldKey, AnswerScope scope, SecurityRequest securityRequest) {
        FieldSchema schema = publicationService.getFieldSchema(businessCase.getPublicationId());
        FieldDefinition field =
                schema.find(fieldKey).orElseThrow(() -> new NotFoundException("게시본에 없는 항목입니다: " + fieldKey));
        if (field.scope() != scope) {
            throw new DomainRuleViolationException("항목 " + fieldKey + " 의 범위는 " + field.scope() + " 입니다");
        }
        if (securityRequest != null && !field.appliesTo(securityRequest.getDocType())) {
            throw new DomainRuleViolationException(
                    "항목 " + fieldKey + " 은(는) " + securityRequest.getDocType() + " 서류에 해당하지 않습니다");
        }
        return field;
    }

    private Answer existingCaseAnswer(Long businessCaseId, String fieldKey) {
        return answerRepository
                .findByBusinessCaseIdAndScopeAndFieldKey(businessCaseId, AnswerScope.CASE, fieldKey)
                .orElseThrow(() -> new NotFoundException("답변이 없는 항목입니다: " + fieldKey));
    }

    private Answer existingRequestAnswer(Long securityRequestId, String fieldKey) {
        return answerRepository
                .findBySecurityRequestIdAndFieldKey(securityRequestId, fieldKey)
                .orElseThrow(() -> new NotFoundException("답변이 없는 항목입니다: " + fieldKey));
    }

    private static void requireRevision(Answer answer, Long expectedRevision) {
        if (expectedRevision == null) {
            throw new DomainRuleViolationException("기존 답변을 고칠 때는 expectedRevision 이 필요합니다: " + answer.getFieldKey());
        }
        answer.requireRevision(expectedRevision);
    }

    private BusinessCase businessCaseOf(SecurityRequest securityRequest) {
        return businessCaseRepository
                .findById(securityRequest.getBusinessCaseId())
                .orElseThrow(() -> new NotFoundException("사업 건을 찾을 수 없습니다: " + securityRequest.getBusinessCaseId()));
    }

    /** 범위와 무관하게 이 신청에서 쓰는 항목인지(CASE 또는 해당 서류의 REQUEST). */
    private FieldDefinition requireAnyScopeField(
            BusinessCase businessCase, String fieldKey, SecurityRequest securityRequest) {
        FieldSchema schema = publicationService.getFieldSchema(businessCase.getPublicationId());
        FieldDefinition field =
                schema.find(fieldKey).orElseThrow(() -> new NotFoundException("게시본에 없는 항목입니다: " + fieldKey));
        if (field.scope() == AnswerScope.REQUEST && !field.appliesTo(securityRequest.getDocType())) {
            throw new DomainRuleViolationException(
                    "항목 " + fieldKey + " 은(는) " + securityRequest.getDocType() + " 서류에 해당하지 않습니다");
        }
        return field;
    }

    private Optional<Answer> findForField(
            BusinessCase businessCase, SecurityRequest securityRequest, FieldDefinition field) {
        return field.scope() == AnswerScope.CASE
                ? answerRepository.findByBusinessCaseIdAndScopeAndFieldKey(
                        businessCase.getId(), AnswerScope.CASE, field.key())
                : answerRepository.findBySecurityRequestIdAndFieldKey(securityRequest.getId(), field.key());
    }

    private static Answer newPlaceholder(
            BusinessCase businessCase, SecurityRequest securityRequest, FieldDefinition field) {
        return field.scope() == AnswerScope.CASE
                ? Answer.staffCheckNeeded(AnswerScope.CASE, businessCase.getId(), null, field.key())
                : Answer.staffCheckNeeded(
                        AnswerScope.REQUEST, businessCase.getId(), securityRequest.getId(), field.key());
    }
}
