package kr.securityadvisor.llm.dto;

import java.time.Instant;
import java.util.List;

/** 인터뷰 응답의 근거는 서버가 실제로 제공한 자료 참조만 포함한다. */
public record InterviewTurnResponse(
        Long interviewTurnId,
        Long businessCaseId,
        String fieldKey,
        String mode,
        String question,
        String reply,
        String proposedValue,
        List<Evidence> evidence,
        long basedRevision,
        Long answerRevision,
        String modelReference,
        boolean flagged,
        boolean applied,
        Instant createdAt) {
    public record Evidence(String tag, String label) {}
}
