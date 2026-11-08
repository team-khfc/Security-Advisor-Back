package kr.securityadvisor.llm.dto;

import java.time.Instant;
import kr.securityadvisor.llm.domain.AnalysisJob;
import kr.securityadvisor.llm.domain.AnalysisStatus;

/** 분석 작업 응답. summary 는 AI 제안(미확정)이다. */
public record AnalysisJobResponse(
        Long analysisJobId,
        Long businessCaseId,
        Long uploadedFileId,
        AnalysisStatus status,
        String statusLabel,
        String summary,
        String modelReference,
        String errorMessage,
        String requestedBy,
        Instant startedAt,
        Instant finishedAt) {

    public static AnalysisJobResponse from(AnalysisJob j) {
        return new AnalysisJobResponse(
                j.getId(),
                j.getBusinessCaseId(),
                j.getUploadedFileId(),
                j.getStatus(),
                j.getStatus().label(),
                j.getSummary(),
                j.getModelReference(),
                j.getErrorMessage(),
                j.getRequestedBy(),
                j.getStartedAt(),
                j.getFinishedAt());
    }
}
