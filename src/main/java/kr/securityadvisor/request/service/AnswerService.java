package kr.securityadvisor.request.service;

import java.time.Clock;
import java.util.List;
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
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.AnswerActionRequest;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.dto.AnswerUpdateRequest;
import kr.securityadvisor.request.repository.AnswerRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
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
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final SecurityRequestRepository securityRequestRepository;
    private final PublicationService publicationService;
    private final RequestAccessPolicy accessPolicy;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public AnswerService(
            AnswerRepository answerRepository,
            SecurityRequestRepository securityRequestRepository,
            PublicationService publicationService,
            RequestAccessPolicy accessPolicy,
            AuditRecorder auditRecorder,
            Clock clock) {
        this.answerRepository = answerRepository;
        this.securityRequestRepository = securityRequestRepository;
        this.publicationService = publicationService;
        this.accessPolicy = accessPolicy;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

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
        return AnswerResponse.from(answer);
    }

    /** 서류별 항목에 사용자 답변을 저장한다. */
    public AnswerResponse answerRequestField(
            MemberPrincipal principal, Long securityRequestId, String fieldKey, AnswerUpdateRequest request) {
        SecurityRequest securityRequest = accessPolicy.ownedRequest(principal, securityRequestId);
        requireRequestEditable(securityRequest);
        BusinessCase businessCase = accessPolicy.viewableCase(principal, securityRequest.getBusinessCaseId());
        FieldDefinition field = requireField(businessCase, fieldKey, AnswerScope.REQUEST, securityRequest);
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
        return AnswerResponse.from(answer);
    }

    /** 사용자가 공통 항목의 AI 제안 값을 확인한다(확인 전까지 미확정, 불변식 1). */
    public AnswerResponse confirmCaseAnswer(
            MemberPrincipal principal, Long businessCaseId, String fieldKey, AnswerActionRequest request) {
        BusinessCase businessCase = accessPolicy.ownedCase(principal, businessCaseId);
        requireCaseEditable(businessCase);
        Answer answer = existingCaseAnswer(businessCaseId, fieldKey);
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
        Answer answer = existingRequestAnswer(securityRequestId, fieldKey);
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

    /** 신청의 서류별 답변 목록. */
    @Transactional(readOnly = true)
    public List<AnswerResponse> requestAnswers(Long securityRequestId) {
        return answerRepository.findBySecurityRequestIdOrderByFieldKeyAsc(securityRequestId).stream()
                .map(AnswerResponse::from)
                .toList();
    }

    private void requireCaseEditable(BusinessCase businessCase) {
        boolean anyUnsubmitted = securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCase.getId()).stream()
                .anyMatch(SecurityRequest::isUnsubmitted);
        if (!anyUnsubmitted) {
            throw new DomainRuleViolationException("진행 중(미제출)인 신청이 없어 공통 답변을 바꿀 수 없습니다");
        }
    }

    private static void requireRequestEditable(SecurityRequest securityRequest) {
        if (!securityRequest.isUnsubmitted()) {
            throw new DomainRuleViolationException(
                    "제출·종결·철회된 신청의 답변은 바꿀 수 없습니다 (현재 " + securityRequest.getStatus().label() + ")");
        }
    }

    /** 항목이 게시본 스키마에 있고 범위·서류 유형이 맞는지 검사한다. */
    private FieldDefinition requireField(
            BusinessCase businessCase, String fieldKey, AnswerScope scope, SecurityRequest securityRequest) {
        FieldSchema schema = publicationService.getFieldSchema(businessCase.getPublicationId());
        FieldDefinition field = schema.find(fieldKey)
                .orElseThrow(() -> new NotFoundException("게시본에 없는 항목입니다: " + fieldKey));
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
}
