package kr.securityadvisor.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 보완 요청(협의의 하위 자원 생성 POST …/supplements).
 *
 * @param fieldKeys 보완 대상 항목 키(1개 이상)
 * @param reason 사유(필수)
 * @param expectedRevision 협의의 revision
 */
public record SupplementRequestCommand(
        @NotEmpty List<String> fieldKeys,
        @NotBlank @Size(max = 2000) String reason,
        @NotNull Long expectedRevision) {}
