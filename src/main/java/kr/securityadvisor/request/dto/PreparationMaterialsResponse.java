package kr.securityadvisor.request.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 조건별 자료와 실제 연결·검토 상태. 과거에 연결한 비해당 자료도 숨겨서 삭제하지 않는다. */
public record PreparationMaterialsResponse(
        long revision,
        String fingerprint,
        Map<String, Boolean> extraConditions,
        boolean editable,
        boolean canSetConditions,
        List<PreparationItem> items) {
    public record PreparationItem(
            String key,
            String label,
            String reviewer,
            String condition,
            String description,
            String applicability,
            String status,
            List<Long> fileIds,
            String note,
            String actor,
            Instant at,
            boolean stale,
            boolean canAttach,
            boolean canReview) {}
}
