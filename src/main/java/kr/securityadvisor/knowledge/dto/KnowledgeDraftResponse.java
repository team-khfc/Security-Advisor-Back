package kr.securityadvisor.knowledge.dto;

import java.time.Instant;

public record KnowledgeDraftResponse(
        Long knowledgeDraftId,
        int basedOnVersion,
        int currentPublishedVersion,
        long revision,
        String note,
        String updatedBy,
        Instant updatedAt,
        long snippetCount,
        long privateContextCount) {}
