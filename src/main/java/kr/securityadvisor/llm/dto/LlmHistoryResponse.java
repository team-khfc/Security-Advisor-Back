package kr.securityadvisor.llm.dto;

import java.time.Instant;

/** 관리자 전용 모델 구성 이력. */
public record LlmHistoryResponse(
        Long historyId,
        Long configurationId,
        long configurationRevision,
        String action,
        String changedBy,
        Instant changedAt,
        String reason,
        Long restoredFromId,
        LlmConfigurationValues values) {}
