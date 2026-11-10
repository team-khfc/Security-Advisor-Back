package kr.securityadvisor.llm.dto;

import java.util.List;
import java.util.Map;

/** 미확정 제안과 필드별 근거. 빈 value는 확인 필요이며 저장값이 아니다. */
public record InfrastructureProposalResponse(
        long baseRevision, List<Connection> connections, List<String> warnings, String modelReference) {
    public record Connection(Map<String, Field> fields) {}

    public record Field(String value, String sourceTag, String sourceLabel, String quote) {}
}
