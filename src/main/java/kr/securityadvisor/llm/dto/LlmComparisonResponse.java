package kr.securityadvisor.llm.dto;

import java.util.List;

/** 검토한 버전과 변경 전후 값. 저장은 같은 revision을 다시 검사한다. */
public record LlmComparisonResponse(
        Long currentRevision, LlmConfigurationValues current, LlmConfigurationValues candidate, List<Change> changes) {
    public record Change(String field, String label, String before, String after) {}
}
