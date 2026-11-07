package kr.securityadvisor.request.service;

import java.util.List;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.request.domain.Answer;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.dto.BusinessCaseListItem;
import kr.securityadvisor.request.dto.BusinessCaseResponse;
import kr.securityadvisor.request.dto.CreateBusinessCaseRequest;
import kr.securityadvisor.request.dto.SecurityRequestSummary;
import kr.securityadvisor.request.dto.UpdateBusinessCaseRequest;
import kr.securityadvisor.request.repository.AnswerRepository;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업 건 유스케이스(F-01). 사업 건 1건 + 선택한 서류 유형별 신청 N건을 한 트랜잭션에서 만들고, 조회·개요 수정을 맡는다.
 */
@Service
@Transactional(readOnly = true)
public class BusinessCaseService {

    private final BusinessCaseRepository businessCaseRepository;
    private final SecurityRequestRepository securityRequestRepository;
    private final AnswerRepository answerRepository;
    private final PublicationService publicationService;
    private final RequestAccessPolicy accessPolicy;

    public BusinessCaseService(
            BusinessCaseRepository businessCaseRepository,
            SecurityRequestRepository securityRequestRepository,
            AnswerRepository answerRepository,
            PublicationService publicationService,
            RequestAccessPolicy accessPolicy) {
        this.businessCaseRepository = businessCaseRepository;
        this.securityRequestRepository = securityRequestRepository;
        this.answerRepository = answerRepository;
        this.publicationService = publicationService;
        this.accessPolicy = accessPolicy;
    }

    /**
     * 사업 건과 서류별 신청을 만든다. 현재 게시본을 고정하고, 사업명·개요·부서는 공통 답변(USER_ANSWER)으로도 저장하며,
     * 담당자가 답할 항목은 "담당자 확인 필요" 자리로 미리 만든다.
     *
     * @throws DomainRuleViolationException 서류 유형이 비었을 때(422)
     */
    @Transactional
    public BusinessCaseResponse create(MemberPrincipal principal, CreateBusinessCaseRequest request) {
        if (request.docTypes() == null || request.docTypes().isEmpty()) {
            throw new DomainRuleViolationException("서류 유형을 하나 이상 선택해야 합니다");
        }
        Long publicationId = publicationService.currentPublicationId();
        FieldSchema schema = publicationService.getFieldSchema(publicationId);

        BusinessCase businessCase = businessCaseRepository.save(BusinessCase.create(
                principal.username(), request.title(), request.summary(), request.department(), publicationId));
        Long caseId = businessCase.getId();

        List<SecurityRequest> requests = request.docTypes().stream()
                .sorted()
                .map(docType -> securityRequestRepository.save(SecurityRequest.create(caseId, docType)))
                .toList();

        prefillCaseAnswers(businessCase, schema);
        for (SecurityRequest securityRequest : requests) {
            prefillRequestPlaceholders(securityRequest, schema);
        }
        // TODO(정용석): audit 컨텍스트 도입 후 사업 건·신청 생성을 audit_events 에 기록한다(F-14).
        return toResponse(businessCase, requests);
    }

    /** 사업 건 상세(공통 답변·하위 신청 요약 포함). */
    public BusinessCaseResponse get(MemberPrincipal principal, Long businessCaseId) {
        BusinessCase businessCase = accessPolicy.viewableCase(principal, businessCaseId);
        return toResponse(businessCase, securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCaseId));
    }

    /** 내 사업 건 목록(신청자). 담당자 요청함은 review 컨텍스트가 제공한다. */
    public PageResponse<BusinessCaseListItem> listMine(MemberPrincipal principal, Pageable pageable) {
        return PageResponse.from(
                businessCaseRepository.findByApplicantUsername(principal.username(), pageable), businessCase -> {
                    List<SecurityRequest> requests =
                            securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCase.getId());
                    return BusinessCaseListItem.from(
                            businessCase,
                            ProgressSummary.of(requests),
                            requests.stream().map(SecurityRequestSummary::from).toList());
                });
    }

    /** 사업 개요 수정. 제출된 신청의 제출본은 스냅샷이라 영향이 없다(불변식 17). */
    @Transactional
    public BusinessCaseResponse updateOutline(
            MemberPrincipal principal, Long businessCaseId, UpdateBusinessCaseRequest request) {
        BusinessCase businessCase = accessPolicy.ownedCase(principal, businessCaseId);
        businessCase.requireRevision(request.expectedRevision());
        businessCase.updateOutline(request.title(), request.summary(), request.department());
        return toResponse(businessCase, securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCaseId));
    }

    private void prefillCaseAnswers(BusinessCase businessCase, FieldSchema schema) {
        Long caseId = businessCase.getId();
        for (FieldDefinition field : schema.caseFields()) {
            String preset = presetValue(field.key(), businessCase);
            if (preset != null) {
                answerRepository.save(Answer.userAnswer(AnswerScope.CASE, caseId, null, field.key(), preset));
            } else if (field.isStaffField()) {
                answerRepository.save(Answer.staffCheckNeeded(AnswerScope.CASE, caseId, null, field.key()));
            }
        }
    }

    private void prefillRequestPlaceholders(SecurityRequest request, FieldSchema schema) {
        for (FieldDefinition field : schema.requestFields(request.getDocType())) {
            if (field.isStaffField()) {
                answerRepository.save(Answer.staffCheckNeeded(
                        AnswerScope.REQUEST, request.getBusinessCaseId(), request.getId(), field.key()));
            }
        }
    }

    /** 생성 요청에서 바로 채울 수 있는 공통 항목(seed 게시본의 저장 키 기준). 값이 없으면 null. */
    private static String presetValue(String fieldKey, BusinessCase businessCase) {
        String value = switch (fieldKey) {
            case "project.name" -> businessCase.getTitle();
            case "project.summary" -> businessCase.getSummary();
            case "project.department" -> businessCase.getDepartment();
            default -> null;
        };
        return (value == null || value.isBlank()) ? null : value;
    }

    private BusinessCaseResponse toResponse(BusinessCase businessCase, List<SecurityRequest> requests) {
        List<AnswerResponse> caseAnswers =
                answerRepository.findByBusinessCaseIdAndScopeOrderByFieldKeyAsc(businessCase.getId(), AnswerScope.CASE).stream()
                        .map(AnswerResponse::from)
                        .toList();
        return BusinessCaseResponse.from(
                businessCase,
                ProgressSummary.of(requests),
                requests.stream().map(SecurityRequestSummary::from).toList(),
                caseAnswers);
    }
}
