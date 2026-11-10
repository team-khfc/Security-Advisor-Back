package kr.securityadvisor.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 생성에서는 신청 revision, 확인에서는 초안 revision을 전달한다. */
public record FinalDraftCommand(
        @NotBlank @Size(max = 12000) String text,
        @NotNull @PositiveOrZero Long expectedRevision) {}
