package kr.securityadvisor.request.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 제출 당시 준비자료와 검토 기록. 권한이나 현재 상태를 과거 제출본에 다시 계산하지 않는다. */
public record PreparationReviewResponse(
        int format,
        long revision,
        String fingerprint,
        Instant capturedAt,
        Map<String, Boolean> extraConditions,
        List<Item> items) {
    public record File(Long fileId, String name, Instant uploadedAt) {}

    public record Item(
            String key,
            String label,
            String reviewer,
            String applicability,
            String status,
            boolean stale,
            String note,
            String actor,
            Instant reviewedAt,
            List<File> files) {}
}
