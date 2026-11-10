package kr.securityadvisor.knowledge.dto;

import java.util.List;

public record KnowledgeComparisonResponse(int fromVersion, Integer toVersion, Long revision, List<Change> changes) {
    public record Change(String kind, Long itemId, String label, String before, String after) {}
}
