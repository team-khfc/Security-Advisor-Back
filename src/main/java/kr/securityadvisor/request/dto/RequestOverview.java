package kr.securityadvisor.request.dto;

import java.time.LocalDate;
import java.util.List;

/** 목록 표시 전용 요약. 답변·AI 원문·제출본을 목록에 싣지 않는다. */
public record RequestOverview(
        boolean myTurn, LocalDate dueDate, Long submissionId, List<FieldSummary> fields, boolean needsReview) {
    /** 게시본에 필요한 분야만 포함하며 최신 제출본의 확인 여부를 표시한다. */
    public record FieldSummary(
            String field, String assigneeUsername, String reviewStatus, boolean allocated, boolean confirmed) {}
}
