package kr.securityadvisor.policy.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import kr.securityadvisor.common.error.DomainRuleViolationException;

/**
 * 허용 기간 값("YYYY-MM-DD~YYYY-MM-DD" 또는 "YYYY-MM-DD~" 상시) 해석. 상시 규칙의 재검토일은 시작일 + 1년(1차 범위 고정값).
 */
public record RulePeriod(LocalDate start, LocalDate end, LocalDate reviewDate) {

    public static final int REVIEW_MONTHS_FOR_OPEN_ENDED = 12;

    public static RulePeriod parse(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException("허용 기간 값이 없습니다");
        }
        String[] parts = value.strip().split("~", -1);
        try {
            LocalDate start = LocalDate.parse(parts[0].strip());
            LocalDate end = parts.length > 1 && !parts[1].isBlank() ? LocalDate.parse(parts[1].strip()) : null;
            if (LocalDate.of(9999, 12, 31).equals(end)) end = null;
            LocalDate review = end == null ? start.plusMonths(REVIEW_MONTHS_FOR_OPEN_ENDED) : null;
            return new RulePeriod(start, end, review);
        } catch (DateTimeParseException e) {
            throw new DomainRuleViolationException("허용 기간 형식이 잘못되었습니다(YYYY-MM-DD~YYYY-MM-DD): " + value);
        }
    }
}
