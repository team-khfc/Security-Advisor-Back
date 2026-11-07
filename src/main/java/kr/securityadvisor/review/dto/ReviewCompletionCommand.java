package kr.securityadvisor.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 협의 완료 요청(단일 하위 자원 설정 PUT …/completion).
 *
 * @param opinion 담당자 의견(필수)
 * @param checklist 분야 체크리스트 항목 키 → 확인 여부(전부 true 여야 완료)
 * @param expectedRevision 협의의 revision
 */
public record ReviewCompletionCommand(
        @NotBlank @Size(max = 4000) String opinion,
        @NotNull Map<String, Boolean> checklist,
        @NotNull Long expectedRevision) {}
