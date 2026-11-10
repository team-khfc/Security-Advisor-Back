package kr.securityadvisor.review.dto;

import java.time.Instant;

/** 현재 권한으로 계산한 작업과 카드 표시용 신청 요약. */
public record TodoItem(String key, String kind, Long securityRequestId, String title, String link, Context context) {
    public TodoItem(String key, String kind, Long securityRequestId, String title, String link) {
        this(key, kind, securityRequestId, title, link, null);
    }

    public record Context(
            String caseTitle, String department, String docType, String statusLabel, int stage, Instant updatedAt) {}
}
