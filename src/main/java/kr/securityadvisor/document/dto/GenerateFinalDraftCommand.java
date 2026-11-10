package kr.securityadvisor.document.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 최종 검토 중인 신청의 버전을 지정한다. */
public record GenerateFinalDraftCommand(
        @NotNull @PositiveOrZero Long expectedRevision) {}
