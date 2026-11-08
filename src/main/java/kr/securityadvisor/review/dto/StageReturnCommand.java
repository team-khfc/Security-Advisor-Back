package kr.securityadvisor.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 차수 반환(POST …/stage-returns). 허용 반환은 2→1, 3→2, 3→1, 4→3.
 *
 * @param targetStage 돌아갈 단계
 * @param reason 사유(필수)
 * @param fieldKeys "보완 필요/재작성 필요"로 지정할 항목 키(선택 — 비우면 항목 강조 없이 단계만 반환)
 * @param fieldReasons 항목별 사유(선택). 키는 항목 키이며 fieldKeys 에 없는 키는 자동으로 지정 항목에 포함된다
 * @param expectedRevision 신청의 revision
 */
public record StageReturnCommand(
        @NotNull @Min(1) @Max(3) Integer targetStage,
        @NotBlank @Size(max = 2000) String reason,
        List<String> fieldKeys,
        Map<String, @Size(max = 2000) String> fieldReasons,
        @NotNull Long expectedRevision) {}
