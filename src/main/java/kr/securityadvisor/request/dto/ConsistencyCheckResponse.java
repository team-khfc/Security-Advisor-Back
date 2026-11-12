package kr.securityadvisor.request.dto;

import java.time.Instant;
import java.util.List;

/** 원문과 현재 저장 답변의 비교 결과. 업무 상태나 답변을 변경하지 않는다. */
public record ConsistencyCheckResponse(
        String fingerprint,
        Instant checkedAt,
        String model,
        int answerCount,
        int totalFiles,
        List<Source> sources,
        List<Finding> findings) {
    public record Source(Long fileId, String name, boolean truncated, boolean empty) {}

    public record Finding(
            String fieldKey,
            String label,
            String answer,
            boolean confirmed,
            Long fileId,
            String quote,
            String explanation) {}
}
