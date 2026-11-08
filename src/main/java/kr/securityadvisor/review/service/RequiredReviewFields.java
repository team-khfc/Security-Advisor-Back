package kr.securityadvisor.review.service;

import java.util.Set;
import java.util.stream.Collectors;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.DocTypeDefinition;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 서류 유형이 요구하는 협의 분야(게시본 {@code docTypes[].reviewFields}, 사업 건 생성 시점 게시본 기준 — 불변식 8).
 * 협의 완료·최종 확인·확정의 "INFRA 선행"과 "모든 분야" 요건은 이 집합을 기준으로 판단한다 — SECURITY 만 요구하는 서류는
 * INFRA 할당·INFRA 최종 확인 없이 끝까지 진행된다.
 */
@Component
@RequiredArgsConstructor
public class RequiredReviewFields {

    private final PublicationService publicationService;
    private final BusinessCaseRepository businessCaseRepository;

    public Set<ReviewField> of(SecurityRequest request) {
        BusinessCase businessCase = businessCaseRepository
                .findById(request.getBusinessCaseId())
                .orElseThrow(() -> new DomainRuleViolationException("사업 건이 없습니다: " + request.getBusinessCaseId()));
        return of(businessCase, request);
    }

    public Set<ReviewField> of(BusinessCase businessCase, SecurityRequest request) {
        DocTypeDefinition docType = publicationService
                .getFieldSchema(businessCase.getPublicationId())
                .findDocType(request.getDocType())
                .orElseThrow(() -> new DomainRuleViolationException("게시본에 없는 서류 유형입니다: " + request.getDocType()));
        Set<ReviewField> fields =
                docType.reviewFields() == null || docType.reviewFields().isEmpty()
                        ? Set.of(ReviewField.INFRA, ReviewField.SECURITY)
                        : docType.reviewFields().stream().map(ReviewField::of).collect(Collectors.toSet());
        if (fields.isEmpty()) {
            throw new DomainRuleViolationException("협의 분야가 없는 서류 유형입니다: " + request.getDocType());
        }
        return fields;
    }

    /** INFRA 선행 규칙이 적용되는지 — INFRA 협의가 요구되는 서류에서만. */
    public boolean infraPrecedes(Set<ReviewField> required) {
        return required.contains(ReviewField.INFRA);
    }

    /** 확정 권한 분야: 후행 분야(SECURITY 가 있으면 SECURITY, 없으면 INFRA). */
    public ReviewField confirmer(Set<ReviewField> required) {
        return required.contains(ReviewField.SECURITY) ? ReviewField.SECURITY : ReviewField.INFRA;
    }
}
