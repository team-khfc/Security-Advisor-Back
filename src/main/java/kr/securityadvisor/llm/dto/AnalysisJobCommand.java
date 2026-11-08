package kr.securityadvisor.llm.dto;

import jakarta.validation.constraints.NotNull;

/** 분석 요청(POST /api/business-cases/{id}/analysis-jobs). */
public record AnalysisJobCommand(@NotNull Long uploadedFileId) {}
