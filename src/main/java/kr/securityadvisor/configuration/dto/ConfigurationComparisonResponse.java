package kr.securityadvisor.configuration.dto;

import java.util.List;

/** 저장한 초안 또는 게시본의 변경 비교. revision은 검토한 초안을 게시할 때 그대로 보낸다. */
public record ConfigurationComparisonResponse(int fromVersion, Integer toVersion, Long revision, List<Change> changes) {
    /** 전후 값은 표시용 문자열이며 저장 키·범위 등 계약 변경을 별도로 알린다. */
    public record Change(String path, String label, String before, String after, boolean dataContractChange) {}
}
