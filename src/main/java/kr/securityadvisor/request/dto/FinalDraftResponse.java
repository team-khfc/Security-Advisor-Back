package kr.securityadvisor.request.dto;

import java.time.Instant;

/** AI 출처와 사람 확인, 원본 변경 여부를 함께 표시하는 최종 서술. */
public record FinalDraftResponse(
        Long finalDraftId,
        Long securityRequestId,
        String text,
        String valueSource,
        String modelReference,
        String confirmedBy,
        Instant confirmedAt,
        boolean confirmed,
        boolean stale,
        long revision) {}
