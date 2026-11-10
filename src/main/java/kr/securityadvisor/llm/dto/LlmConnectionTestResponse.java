package kr.securityadvisor.llm.dto;

import java.time.Instant;

/** 시험 자원의 성공·실패를 명시하며 외부 응답 원문은 반환하지 않는다. */
public record LlmConnectionTestResponse(
        Long testId,
        Long configurationId,
        Long configurationRevision,
        String status,
        String outcome,
        String message,
        long elapsedMillis,
        Instant testedAt,
        boolean stale,
        LlmConfigurationValues testedValues) {}
