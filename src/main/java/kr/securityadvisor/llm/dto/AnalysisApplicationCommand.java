package kr.securityadvisor.llm.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 분석 결과 적용(POST /api/analysis-jobs/{id}/applications). 사업 건 revision 을 확인해 오래된 화면의 적용을 막는다(409).
 *
 * @param expectedRevision 사업 건의 revision
 */
public record AnalysisApplicationCommand(@NotNull Long expectedRevision) {}
