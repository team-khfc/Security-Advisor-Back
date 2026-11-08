package kr.securityadvisor.document.dto;

/**
 * 서류의 한 칸: 항목·값·출처·확인 상태. 값이 없거나 미확정이면 {@code display} 가 "담당자 확인 필요"/"미입력" 이다(F-06).
 *
 * @param valueSource USER_ANSWER / AI_INFERRED / STAFF_INPUT / STAFF_CONFIRMED / null(출처 없음)
 */
public record DocumentValue(
        String fieldKey,
        String label,
        String scope,
        String value,
        String valueSource,
        boolean confirmed,
        boolean needsReview,
        String display,
        String status) {}
