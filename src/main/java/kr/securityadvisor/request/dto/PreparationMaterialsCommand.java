package kr.securityadvisor.request.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

/** ATTACH·REVIEW·CONDITIONS를 구분해 신청자의 확인 완료 우회를 막는다. */
public record PreparationMaterialsCommand(
        @NotNull @PositiveOrZero Long expectedRevision,
        @NotBlank @Size(max = 64) String expectedFingerprint,

        @NotBlank @Pattern(regexp = "ATTACH|REVIEW|CONDITIONS")
        String action,

        @Size(max = 40) String key,
        @Size(max = 30) String status,
        @Size(max = 10) List<@NotNull @Positive Long> fileIds,
        @Size(max = 2000) String note,
        @Size(max = 2) Map<String, Boolean> conditions) {}
