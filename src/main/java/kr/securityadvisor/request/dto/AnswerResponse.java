package kr.securityadvisor.request.dto;

import java.time.Instant;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.ValueSource;
import kr.securityadvisor.request.domain.Answer;

/**
 * 답변 응답. 화면은 valueSource·confirmed 로 "AI 제안(미확정)"·"담당자 확인 필요"·"확인 완료"를 구분해 표시한다.
 *
 * @param valueSource null 이면 담당자 확인 필요
 */
public record AnswerResponse(
        Long answerId,
        String fieldKey,
        AnswerScope scope,
        String value,
        ValueSource valueSource,
        String evidence,
        boolean confirmed,
        String confirmedBy,
        Instant confirmedAt,
        boolean needsReview,
        long revision,
        boolean notApplicable) {

    public static AnswerResponse from(Answer answer) {
        return new AnswerResponse(
                answer.getId(),
                answer.getFieldKey(),
                answer.getScope(),
                answer.getValue(),
                answer.getValueSource(),
                answer.getEvidence(),
                answer.isConfirmed(),
                answer.getConfirmedBy(),
                answer.getConfirmedAt(),
                answer.isNeedsReview(),
                answer.getRevision(),
                answer.isNotApplicable());
    }
}
