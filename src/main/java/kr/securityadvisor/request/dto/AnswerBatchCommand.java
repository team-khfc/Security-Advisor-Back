package kr.securityadvisor.request.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 답변 일괄 저장(PATCH …/answers). 항목마다 `PUT …/answers/{key}` 와 같은 규칙(값·항목별 expectedRevision)을 적용하고
 * 한 트랜잭션에서 전부 저장하거나 전부 실패한다(409·422 는 첫 실패 항목 기준).
 *
 * @param answers 저장할 항목(1개 이상)
 */
public record AnswerBatchCommand(@NotEmpty @Size(max = 100) List<@Valid Item> answers) {

    /** @param expectedRevision 기존 답변의 revision(새 항목이면 생략) */
    public record Item(
            @NotBlank @Size(max = 100) String fieldKey,
            @Size(max = 4000) String value,
            Long expectedRevision,
            Boolean notApplicable) {
        @com.fasterxml.jackson.annotation.JsonCreator
        public Item {
            notApplicable = Boolean.TRUE.equals(notApplicable);
        }

        public Item(String fieldKey, String value, Long expectedRevision) {
            this(fieldKey, value, expectedRevision, false);
        }
    }
}
