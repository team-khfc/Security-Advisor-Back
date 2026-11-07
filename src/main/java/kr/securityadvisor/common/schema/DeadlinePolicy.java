package kr.securityadvisor.common.schema;

import java.time.LocalDate;
import java.util.Set;

/**
 * 처리 기한 정책(F-13). 게시본에 포함되어 사업 건 생성 시점 버전으로 고정된다. 값이 없으면 기본 5영업일.
 *
 * @param reviewBusinessDays 분야별 협의 기한(배정 시각 기준 영업일)
 * @param supplementBusinessDays 보완 응답 기한(보완 요청·차수 반환 시각 기준)
 * @param finalConfirmationBusinessDays 최종 확정 기한(제출 시각 기준)
 * @param imminentBusinessDays 기한 임박으로 표시할 남은 영업일(기본 1)
 * @param holidays 기관 공휴일(합성 seed)
 */
public record DeadlinePolicy(
        Integer reviewBusinessDays,
        Integer supplementBusinessDays,
        Integer finalConfirmationBusinessDays,
        Integer imminentBusinessDays,
        Set<LocalDate> holidays) {

    public static final int DEFAULT_BUSINESS_DAYS = 5;

    public DeadlinePolicy {
        reviewBusinessDays = positiveOrDefault(reviewBusinessDays, DEFAULT_BUSINESS_DAYS);
        supplementBusinessDays = positiveOrDefault(supplementBusinessDays, DEFAULT_BUSINESS_DAYS);
        finalConfirmationBusinessDays = positiveOrDefault(finalConfirmationBusinessDays, DEFAULT_BUSINESS_DAYS);
        imminentBusinessDays = positiveOrDefault(imminentBusinessDays, 1);
        holidays = holidays == null ? Set.of() : Set.copyOf(holidays);
    }

    public static DeadlinePolicy defaults() {
        return new DeadlinePolicy(null, null, null, null, null);
    }

    private static Integer positiveOrDefault(Integer value, int fallback) {
        return (value == null || value < 1) ? fallback : value;
    }
}
