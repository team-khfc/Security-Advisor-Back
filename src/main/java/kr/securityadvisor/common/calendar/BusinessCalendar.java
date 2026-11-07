package kr.securityadvisor.common.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * 영업일 계산 도메인 서비스(F-13). 주말과 기관 공휴일을 제외한다. 상태를 갖지 않는 순수 계산이다.
 */
public final class BusinessCalendar {

    private final Set<LocalDate> holidays;

    public BusinessCalendar(Set<LocalDate> holidays) {
        this.holidays = holidays == null ? Set.of() : Set.copyOf(holidays);
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

    /** 기산일 다음 영업일부터 세어 n번째 영업일. n은 1 이상. */
    public LocalDate addBusinessDays(LocalDate from, int businessDays) {
        if (businessDays < 1) {
            throw new IllegalArgumentException("영업일 수는 1 이상이어야 합니다: " + businessDays);
        }
        LocalDate date = from;
        int counted = 0;
        while (counted < businessDays) {
            date = date.plusDays(1);
            if (isBusinessDay(date)) {
                counted++;
            }
        }
        return date;
    }

    /** from(미포함)부터 to(포함)까지 남은 영업일 수. to 가 from 이전이면 음수(지난 영업일 수의 음수). */
    public int businessDaysBetween(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return -businessDaysBetween(to, from);
        }
        int count = 0;
        LocalDate date = from;
        while (date.isBefore(to)) {
            date = date.plusDays(1);
            if (isBusinessDay(date)) {
                count++;
            }
        }
        return count;
    }
}
