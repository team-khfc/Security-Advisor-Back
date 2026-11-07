package kr.securityadvisor.request.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.request.domain.Answer;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.domain.SubmissionPreconditions;
import kr.securityadvisor.request.repository.AnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 제출 전 검사 사실 수집(F-07): 필수값 누락·미확정 값은 답변에서 계산하고, 보완·협의 사실은 호출자(review 컨텍스트)가 넘긴다.
 * 판단 자체는 {@link SecurityRequest#submit} 이 한다.
 */
@Component
@RequiredArgsConstructor
public class SubmissionChecker {

    private final AnswerRepository answerRepository;
    private final PublicationService publicationService;

    public SubmissionPreconditions check(
            BusinessCase businessCase, SecurityRequest request, int openSupplementCount, boolean allReviewsCompleted) {
        FieldSchema schema = publicationService.getFieldSchema(businessCase.getPublicationId());
        Map<String, Answer> answers = answersByKey(businessCase, request);

        List<String> missing = new ArrayList<>();
        List<String> unconfirmed = new ArrayList<>();
        for (FieldDefinition field : schema.requiredFieldsFor(request.getDocType())) {
            Answer answer = answers.get(field.key());
            if (answer == null || !answer.hasValue()) {
                missing.add(field.key());
            }
        }
        for (Answer answer : answers.values()) {
            if (answer.isUnconfirmed()) {
                unconfirmed.add(answer.getFieldKey());
            }
        }
        return new SubmissionPreconditions(missing, unconfirmed, openSupplementCount, allReviewsCompleted);
    }

    /** 공통 답변 + 서류별 답변을 저장 키로 모은다(제출본 스냅샷의 재료이기도 하다). */
    public Map<String, Answer> answersByKey(BusinessCase businessCase, SecurityRequest request) {
        List<Answer> all = new ArrayList<>(answerRepository.findByBusinessCaseIdAndScopeOrderByFieldKeyAsc(
                businessCase.getId(), AnswerScope.CASE));
        all.addAll(answerRepository.findBySecurityRequestIdOrderByFieldKeyAsc(request.getId()));
        return all.stream().collect(Collectors.toMap(Answer::getFieldKey, Function.identity(), (a, b) -> b));
    }
}
